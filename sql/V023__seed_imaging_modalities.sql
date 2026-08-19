-- Baseline values for the configurable imaging modality dictionary.
INSERT IGNORE INTO imaging_modality
    (code,name,name_zh,abbreviation,category,description,requires_contrast,typical_duration_min,sort_order)
VALUES
    ('MRI','Magnetic Resonance Imaging','MRI','MRI','STRUCTURAL','Structural brain imaging',0,30,10),
    ('FMRI','Functional Magnetic Resonance Imaging','fMRI','fMRI','FUNCTIONAL','Resting-state or task functional imaging',0,45,20),
    ('DTI','Diffusion Tensor Imaging','DTI','DTI','DIFFUSION','White-matter diffusion imaging',0,20,30),
    ('PET','Positron Emission Tomography','PET','PET','MOLECULAR','Metabolic or molecular tracer imaging',1,60,40),
    ('CT','Computed Tomography','CT','CT','STRUCTURAL','X-ray structural tomography',0,10,50),
    ('EEG','Electroencephalography','EEG','EEG','ELECTROPHYSIOLOGY','Electrophysiology signals and topography',0,60,60);
