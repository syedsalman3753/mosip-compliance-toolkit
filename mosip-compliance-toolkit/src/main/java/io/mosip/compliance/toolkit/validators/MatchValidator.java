package io.mosip.compliance.toolkit.validators;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.mosip.compliance.toolkit.constants.AppConstants;
import io.mosip.compliance.toolkit.dto.testcases.ValidationInputDto;
import io.mosip.compliance.toolkit.dto.testcases.ValidationResultDto;
import io.mosip.kernel.biometrics.constant.BiometricType;
import io.mosip.kernel.biometrics.constant.Match;
import io.mosip.kernel.biometrics.model.Decision;
import io.mosip.kernel.biometrics.model.MatchDecision;

public class MatchValidator extends SDKValidator {

	private Gson gson = new GsonBuilder().serializeNulls().create();

	@Override
	public ValidationResultDto validateResponse(ValidationInputDto inputDto) {
		ValidationResultDto validationResultDto = new ValidationResultDto();
		try {
			ObjectNode methodResponse = (ObjectNode) objectMapperConfig.objectMapper()
					.readValue(inputDto.getMethodResponse(), ObjectNode.class);

			JsonNode mainResponse = (JsonNode) methodResponse.get("response");

			JsonNode matchResp = (JsonNode) mainResponse.get("response");

			MatchDecision[] matchDecisionsArr = gson.fromJson(matchResp.toString(), MatchDecision[].class);

			int statusCode = Integer.parseInt(mainResponse.get("statusCode").asText());

			if (statusCode >= 200 && statusCode <= 299) {
				Map<Integer, Map<BiometricType, Boolean>> resultsMap = new HashMap<Integer, Map<BiometricType, Boolean>>();

				Map<Integer, List<MatchDecision>> galleryDecisionsMap = new HashMap<Integer, List<MatchDecision>>();
				for (MatchDecision item : matchDecisionsArr) {
					Integer galleryIndex = Integer.valueOf(item.getGalleryIndex());
					if (galleryDecisionsMap.get(galleryIndex) != null) {
						List<MatchDecision> list = galleryDecisionsMap.get(galleryIndex);
						list.add(item);
						galleryDecisionsMap.put(galleryIndex, list);
					} else {
						List<MatchDecision> newList = new ArrayList<MatchDecision>();
						newList.add(item);
						galleryDecisionsMap.put(galleryIndex, newList);
					}
				}
				for (Map.Entry<Integer, List<MatchDecision>> entry : galleryDecisionsMap.entrySet()) {
					Integer galleryIndex = entry.getKey();
					List<Map<BiometricType, Decision>> matchDecisions = entry.getValue().stream()
							.map(matchDecision -> matchDecision.getDecisions()).collect(Collectors.toList());
					for (Map<BiometricType, Decision> matchDecision : matchDecisions) {
						for (Entry<BiometricType, Decision> entry1 : matchDecision.entrySet()) {
							BiometricType biometricType = entry1.getKey();
							String matchValue = entry1.getValue().getMatch().name();
							if (!inputDto.isNegativeTestCase()) {
								if (Match.MATCHED.toString().equals(matchValue)) {
									setResults(resultsMap, galleryIndex, biometricType, Boolean.TRUE);
								} else {
									setResults(resultsMap, galleryIndex, biometricType, Boolean.FALSE);
								}
							} else {
								if (Match.NOT_MATCHED.toString().equals(matchValue)) {
									setResults(resultsMap, galleryIndex, biometricType, Boolean.TRUE);
								} else {
									setResults(resultsMap, galleryIndex, biometricType, Boolean.FALSE);
								}
							}
						}
					}
				}

				boolean positiveSuccess = false;
				boolean negativeSuccess = false;
				String results = "";
				for (Map.Entry<Integer, Map<BiometricType, Boolean>> rsEntry : resultsMap.entrySet()) {
					Integer galleryIndex = rsEntry.getKey();
					Map<BiometricType, Boolean> bioMap = rsEntry.getValue();
					for (Map.Entry<BiometricType, Boolean> entry2 : bioMap.entrySet()) {
						if (entry2.getValue()) {
							if (!inputDto.isNegativeTestCase()) {
								positiveSuccess = true;
								results += "Positive Match for " + entry2.getKey().toString()
										+ " is successful for galleryIndex: " + galleryIndex;
							} else {
								negativeSuccess = true;
								results += "Negative Match for " + entry2.getKey().toString()
										+ " is successful for galleryIndex: " + galleryIndex;
							}
						} else {
							if (!inputDto.isNegativeTestCase()) {
								results += "Positive Match for " + entry2.getKey().toString()
										+ " failed for galleryIndex: " + galleryIndex;
							} else {
								results += "Negative Match for " + entry2.getKey().toString()
										+ " failed for galleryIndex: " + galleryIndex;
							}
						}
						results += "\r\n";
					}
				}
				if (positiveSuccess || negativeSuccess) {
					validationResultDto.setStatus(AppConstants.SUCCESS);
				} else {
					validationResultDto.setStatus(AppConstants.FAILURE);
				}
				validationResultDto.setDescription(results);
			} else {
				validationResultDto.setStatus(AppConstants.FAILURE);
				validationResultDto.setDescription("Match status code failed, received: " + statusCode);
			}
		} catch (

		Exception e) {
			validationResultDto.setStatus(AppConstants.FAILURE);
			validationResultDto.setDescription(e.getLocalizedMessage());
			return validationResultDto;
		}
		return validationResultDto;
	}

	private void setResults(Map<Integer, Map<BiometricType, Boolean>> resultsMap, Integer galleryIndex,
			BiometricType biometricType, Boolean result) {
		if (resultsMap.get(galleryIndex) != null) {
			Map<BiometricType, Boolean> bioResults = resultsMap.get(galleryIndex);
			bioResults.put(biometricType, result);
			resultsMap.put(galleryIndex, bioResults);
		} else {
			Map<BiometricType, Boolean> bioResults = new HashMap<BiometricType, Boolean>();
			bioResults.put(biometricType, result);
			resultsMap.put(galleryIndex, bioResults);
		}
	}

}
