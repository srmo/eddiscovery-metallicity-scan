SELECT allFound.EventTime, json_extract(allFound.EventData, '$.SystemName') as SystemName
	FROM JournalEntries AS allFound, 
		 json_each(allFound.EventData) AS jsonAllFound
WHERE allFound.EventType='FSSAllBodiesFound'
AND	  jsonAllFound.key='SystemName'
AND EXISTS 
	(SELECT 1 FROM JournalEntries AS jumpEntries, 
			 json_each(jumpEntries.EventData) AS jsonJe2
		WHERE jumpEntries.EventType='FSDJump'
		AND   jsonJe2.key='StarSystem'
		AND	  jsonJe2.value=jsonAllFound.value
	) -- Why? To make sure a visit occurred?
AND EXISTS
	(SELECT 1 FROM JournalEntries AS starCandidates
		WHERE starCandidates.EventType='Scan'
		AND   json_extract(starCandidates.EventData, '$.BodyName') IS jsonAllFound.value
		AND   json_extract(starCandidates.EventData, '$.StarType') IN ('K', 'F', 'G')
	) -- Only check the primary star. TGMS doesn't care about secondary stars of the right type.
	-- Also list systems without gas giants. Whether the gas giant exists is a datapoint wanted by the survey.
GROUP BY SystemName -- Make eliminating stray duplicate entries easier.
ORDER BY allfound.EventTime DESC