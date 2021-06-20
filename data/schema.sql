# create ы

CREATE TABLE `Complaint` (
  `id`                 int(10) AUTO_INCREMENT NOT NULL,
  `PatientName`        varchar(255)           NOT NULL,
  `PatientSurname`     varchar(255)           NOT NULL,
  `PatientPatronymic`  varchar(255)           NOT NULL,
  `ComplaintDate`      date                   NOT NULL,
  `TreatmentStartDate` date                   NOT NULL,
  PRIMARY KEY (`id`)
);

CREATE TABLE `Dates` (
  `id`        int(10) AUTO_INCREMENT NOT NULL,
  `DateStart` date                   NOT NULL,
  `DateEnd`   date                   NOT NULL,
  PRIMARY KEY (`id`)
);

CREATE TABLE `Department` (
  `id`             int(10) AUTO_INCREMENT NOT NULL,
  `DepartmentName` int(10)                NOT NULL,
  PRIMARY KEY (`id`)
);

CREATE TABLE `PatientGroup` (
  `id`  int(10) AUTO_INCREMENT NOT NULL,
  `Sex` bit                    NOT NULL,
  `Age` int(10)                NOT NULL,
  PRIMARY KEY (`id`)
);

CREATE TABLE `Procedure` (
  `id`            int(10) AUTO_INCREMENT NOT NULL,
  `ProcedureName` varchar(255)           NOT NULL,
  PRIMARY KEY (`id`)
);

CREATE TABLE `Treatment` (
  `id` int(10) AUTO_INCREMENT NOT NULL,
  PRIMARY KEY (`id`)
);

CREATE TABLE `Doctor` (
  `id`           int(10) AUTO_INCREMENT NOT NULL,
  `Name`         varchar(255)           NOT NULL,
  `Surname`      varchar(255)           NOT NULL,
  `Patronymic`   varchar(255)           NOT NULL,
  `DepartmentID` int(10)                NOT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `Doctor_DepartmentID_Department_id_foreign` FOREIGN KEY (`DepartmentID`) REFERENCES `Department`(`id`)
);

CREATE TABLE `Result` (
  `id`          int(10) AUTO_INCREMENT NOT NULL,
  `ResultType`  varchar(255)           NOT NULL,
  `ComplaintID` int(10) DEFAULT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `Result_ComplaintID_Complaint_id_foreign` FOREIGN KEY (`ComplaintID`) REFERENCES `Complaint`(`id`)
);

CREATE TABLE `TreatmentProcedure` (
  `TreatmentID`   int(10) NOT NULL,
  `ProcedureID`   int(10) NOT NULL,
  `ProcedureDone` bit     NOT NULL,
  CONSTRAINT `table8_Treatment_j5j403cym_foreign` FOREIGN KEY (`TreatmentID`) REFERENCES `Treatment`(`id`),
  CONSTRAINT `table8_column_2_Procedure_id_foreign` FOREIGN KEY (`ProcedureID`) REFERENCES `Procedure`(`id`)
);

CREATE TABLE `Assesment` (
  `id`             int(10) AUTO_INCREMENT NOT NULL,
  `DoctorID`       int(10)                NOT NULL,
  `DateID`         int(10)                NOT NULL,
  `TreatmentID`    int(10)                NOT NULL,
  `PatientGroupID` int(10)                NOT NULL,
  `ResultID`       int(10)                NOT NULL,
  `Correctness`    bit                    NOT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `Assesment_DateID_Dates_id_foreign` FOREIGN KEY (`DateID`) REFERENCES `Dates`(`id`),
  CONSTRAINT `Assesment_DoctorID_Doctor_id_foreign` FOREIGN KEY (`DoctorID`) REFERENCES `Doctor`(`id`),
  CONSTRAINT `Assesment_TreatmentID_Treatment_id_foreign` FOREIGN KEY (`TreatmentID`) REFERENCES `Treatment`(`id`),
  CONSTRAINT `Assesment_PatientGroupID_PatientGroup_id_foreign` FOREIGN KEY (`PatientGroupID`) REFERENCES `PatientGroup`(`id`),
  CONSTRAINT `Assesment_ResultID_Result_id_foreign` FOREIGN KEY (`ResultID`) REFERENCES `Result`(`id`)
);

# количество случаев без ошибок в указанный интервал
SELECT SUM(Correctness)
FROM `Assesment` a
       INNER JOIN Dates d on a.DateID = d.id
WHERE DateStart BETWEEN '2021-06-01' AND '2021-06-31';
# количество случаев без ошибок в указанный интервал с указанным последствием
SELECT SUM(Correctness)
FROM `Assesment` a
       INNER JOIN Dates d on a.DateID = d.id
       INNER JOIN Result r on a.ResultID = r.id
WHERE (DateStart BETWEEN '2021-06-01' AND '2021-06-31') AND (ResultType = 'Complaint');

# количество случаев без ошибок в указанный интервал с указанным последствием с указанным доктором
SELECT SUM(Correctness)
FROM `Assesment` a
       INNER JOIN Dates d on a.DateID = d.id
       INNER JOIN Result r on a.ResultID = r.id
       INNER JOIN Doctor doc on a.DoctorID = doc.id
WHERE (DateStart BETWEEN '2021-06-01' AND '2021-06-31') AND (ResultType = 'Complaint') AND (DoctorID = 1);

# количество не сделанных случаев для каждой существующей процедуры
SELECT ProcedureName, COUNT(*) AS "Number of fails"
FROM `Procedure`
       INNER JOIN TreatmentProcedure tp ON tp.ProcedureID = Procedure.id
       INNER JOIN Treatment t ON tp.TreatmentID = t.id
       INNER JOIN Assesment a ON t.id = a.TreatmentID
WHERE ProcedureDone = 0
GROUP BY ProcedureName;

# вывод процедур, которые сделаны в лечении по которому поступило обращение от пациента
SELECT ProcedureName, ProcedureDone
FROM `Procedure`
       INNER JOIN TreatmentProcedure tp ON tp.ProcedureID = Procedure.id
       INNER JOIN Treatment t ON tp.TreatmentID = t.id
       INNER JOIN Assesment a ON t.id = a.TreatmentID
       INNER JOIN Result r ON a.ResultID = r.id
       INNER JOIN Complaint c ON r.ComplaintID = c.id
WHERE (PatientSurname = 'Vasilev') AND (ComplaintDate = '2021-06-02');

###############################################
# динамика - график
# косячные процедуры - pie chart
# косячные врачи - pie chart
# косячные отделы - pie chart
# процент косяков за период - текст
# сколько косячат на тех или иных группах пациентов- pie chart
