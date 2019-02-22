SELECT EventTime, json_extract(EventData, '$.BodyName') AS BodyName
		FROM JournalEntries
		WHERE EventType IS 'Scan' AND substr(BodyName,-2,1)!=' '
		AND json_extract(EventData, '$.StarType') IN ('K', 'F', 'G')
GROUP BY BodyName
ORDER BY EventTime DESC
