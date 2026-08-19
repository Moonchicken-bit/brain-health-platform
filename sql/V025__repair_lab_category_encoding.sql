-- Normalize corrupted local seed values to the stable category codes used by the web app.
UPDATE lab_test_item SET category = 'CSFBiomarker' WHERE id BETWEEN 1 AND 5;
UPDATE lab_test_item SET category = 'Biochemistry' WHERE id BETWEEN 6 AND 11;
UPDATE lab_test_item SET category = 'BloodRoutine' WHERE id BETWEEN 12 AND 15;
UPDATE lab_test_item SET category = 'Biochemistry' WHERE id BETWEEN 16 AND 19;
UPDATE lab_test_item SET category = 'Hormone' WHERE id BETWEEN 20 AND 22;
UPDATE lab_test_item SET category = 'Biochemistry' WHERE id IN (23, 24, 26);
UPDATE lab_test_item SET category = 'Immunology' WHERE id = 25;
UPDATE lab_test_item SET category = 'Other' WHERE id = 27;

UPDATE lab_test_item SET unit = 'μmol/L' WHERE id IN (18, 26);
UPDATE lab_result SET unit = 'μmol/L' WHERE lab_test_id IN (18, 26);
