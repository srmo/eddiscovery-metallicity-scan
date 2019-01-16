SELECT json_extract(allFound.EventData, '$.SystemName'), allFound.EventTime
	FROM JournalEntries AS allFound, 
		 json_each(allFound.EventData) AS jsonAllFound
WHERE allFound.EventType='FSSAllBodiesFound'
AND	  jsonAllFound.key='SystemName'
AND EXISTS 
	(SELECT 1 
		FROM JournalEntries jumpEntries, 
			 json_each(jumpEntries.EventData) AS jsonJe2
		WHERE jumpEntries.EventType='FSDJump'
		AND   jsonJe2.key='StarSystem'
		AND	  jsonJe2.value=jsonAllFound.value
	)
AND EXISTS
	(SELECT 1
		FROM JournalEntries starCandidates
		WHERE starCandidates.EventType='Scan'
		AND   json_extract(starCandidates.EventData, '$.BodyName') LIKE jsonAllFound.value || ' %'
		AND   json_extract(starCandidates.EventData, '$.StarType') IN ('K', 'F', 'G')
	)
AND EXISTS
	(SELECT 1
		FROM JournalEntries gasGiant
		WHERE gasGiant.EventType='Scan'
		AND   json_extract(gasGiant.EventData, '$.BodyName') LIKE jsonAllFound.value || '%'
		AND   json_extract(gasGiant.EventData, '$.PlanetClass') LIKE '%gas%' COLLATE NOCASE
	)
ORDER BY allFound.EventTime
	