SELECT allFound.EventTime, allFound.SystemName
FROM (
		SELECT EventTime, json_extract(EventData, '$.SystemName') AS SystemName
		FROM JournalEntries
		WHERE EventType IS 'FSSAllBodiesFound'
	) AS allFound -- nested query to eliminate as many lines as possible before the join, for speed
	INNER JOIN (
		SELECT EventData
		FROM JournalEntries
		WHERE EventType IS 'Scan'
	) AS starCandidates -- nested query to eliminate as many lines as possible before the join, for speed
	ON json_extract(starCandidates.EventData, '$.BodyName') IS allFound.SystemName
WHERE json_extract(starCandidates.EventData, '$.StarType') IN ('K', 'F', 'G')
GROUP BY allFound.SystemName -- Make eliminating stray duplicate entries easier.
ORDER BY allFound.EventTime DESC
