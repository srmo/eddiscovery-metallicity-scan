SELECT allFound.EventTime,
 json_extract(allFound.EventData, '$.SystemName') as SystemName
	FROM JournalEntries AS allFound, 
		 json_each(allFound.EventData) AS jsonAllFound
	INNER JOIN JournalEntries AS starCandidates ON 
	-- Only check the primary star. TGMS doesn't care about secondary stars of the right type.
	-- Also list systems without gas giants. Whether the gas giant exists is a datapoint wanted by the survey.
		starCandidates.EventType='Scan'
		AND   json_extract(starCandidates.EventData, '$.BodyName') IS jsonAllFound.value
		AND   json_extract(starCandidates.EventData, '$.StarType') IN ('K', 'F', 'G')
WHERE allFound.EventType='FSSAllBodiesFound'
AND	  jsonAllFound.key='SystemName'
GROUP BY SystemName -- Make eliminating stray duplicate entries easier.
ORDER BY allfound.EventTime DESC
