SELECT max(allFound.EventTime), allFound.SystemName
FROM (
		SELECT MAX(EventTime) AS EventTime, json_extract(EventData, '$.SystemName') AS SystemName
		FROM JournalEntries
		WHERE EventType IS 'FSSAllBodiesFound'
		GROUP BY SystemName
	) AS allFound -- nested query to eliminate as many lines as possible before the join, for speed
	INNER JOIN (
		SELECT json_extract(EventData, '$.BodyName') AS BodyName
		FROM JournalEntries
		WHERE EventType IS 'Scan'
		AND json_extract(EventData, '$.StarType') IN ('K', 'F', 'G')
	) AS starCandidates -- nested query to eliminate as many lines as possible before the join, for speed
	ON starCandidates.BodyName IS allFound.SystemName
GROUP BY SystemName	
ORDER BY allFound.EventTime DESC
