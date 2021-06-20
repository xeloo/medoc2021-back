package xak.med2021.api;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jetty.util.ajax.JSON;

import xak.med2021.model.Diagnose;
import xak.med2021.model.Doctor;
import xak.med2021.model.Patient;
import xak.med2021.model.Procedure;

public class RefModule extends BaseModule {

    private final List<Doctor> doctorList = new ArrayList<>();
    private final Map<String, Doctor> doctorMap = new HashMap<>();

    private final List<Patient> patientList = new ArrayList<>();
    private final Map<String, Patient> patientMap = new HashMap<>();

    private final List<String[]> diagList = new ArrayList<>();
    private final Map<String, String> diagMap = new HashMap<>();
    private final Map<String, Map<String, String>> diagServiceMap = new HashMap<>();

    private final Map<String, Criteria> diagToCriteria = new HashMap<>();

    private final List<String[]> serviceList = new ArrayList<>();
    private final Map<String, String> serviceMap = new HashMap<>();
    private final Map<String, String[]> serviceSearch = new HashMap<>();
    private final Map<String, List<String[]>> serviceHint = new HashMap<>();

    private final List<String[]> atxList = new ArrayList<>(); // лекарство
    private final Map<String, String> atxMap = new HashMap<>();
    private final Map<String, String[]> atxSearch = new HashMap<>();
    private final Map<String, List<String[]>> atxHint = new HashMap<>();

    private final Object chartData;

    public RefModule() {

        readServiceList();
        readATXList();
        readDiagnoseList();
        readCriteriaList();
        readDiagServList();
        readDoctorList();
        readPatientList();
        readTreatmentList();
        chartData = loadChartData();

        path("/api", () -> {
            method("getDiagnoseList", this::getDiagnoseList);
            method("getDiagnose", this::getDiagnose);
            method("getCriteria", this::getCriteria);
            method("getServiceList", this::getServiceList);
            method("getDrugList", this::getDrugList);
            method("getChartData", this::getChartData);
            method("getDoctorList", this::getDoctorList);
            method("getDoctor", this::getDoctor);
            method("getPatientList", this::getPatientList);
            method("getPatient", this::getPatient);
        });
    }

    private List<Doctor> getDoctorList() {
        return doctorList;
    }

    private Doctor getDoctor() {
        String id = param("id").getString();
        return doctorMap.get(id);
    }

    private List<Patient> getPatientList() {
        return patientList.stream().map(p -> p.copy()).collect(Collectors.toList());
    }

    private Patient getPatient() {
        String id = param("id").getString();
        return patientMap.get(id);
    }

    private Diagnose getDiagnose() {
        String id = param("id").getString();
        Diagnose d = new Diagnose();
        d.id = id;
        d.title = diagMap.get(id);
        d.services = diagServiceMap.get(id);
        return d;
    }

    private Object loadChartData() {
        try {
            return JSON.parse(Files.readString(Path.of("data/data.json")));
        } catch( IOException e ) {
            e.printStackTrace();
            System.exit(1);
            return null;
        }
    }

    private Object getChartData() {
        return chartData;
    }


    private List<String[]> getDiagnoseList() {
        return diagList;
    }

    private Criteria getCriteria() {
        String diag = param("diag").getString();
        Criteria criteria = diagToCriteria.get(diag);
        if( criteria == null && diag.contains(".") ) {
            criteria = diagToCriteria.get(diag.substring(0, diag.indexOf('.')));
        }
        return criteria;
    }

    private List<String[]> getServiceList() {
        int max = param("max").getInt();
        if( max == 0 )
            max = 10;

        String query = param("q").getString();

        List<String[]> list;
        if( query == null || query.isEmpty() ) {
            list = serviceList;
        } else {
            query = query.toLowerCase();
            list = serviceHint.computeIfAbsent(query, q -> {
                List<String[]> l = new ArrayList<>();
                for( String s : serviceSearch.keySet() ) {
                    if( s.contains(q) ) {
                        l.add(serviceSearch.get(s));
                    }
                }

                return l;
            });
        }

        return list.subList(0, Math.min(max, list.size()));
    }

    private List<String[]> getDrugList() {
        int max = param("max").getInt();
        if( max == 0 )
            max = 10;

        String query = param("q").getString();

        List<String[]> list;
        if( query == null || query.isEmpty() ) {
            list = atxList;
        } else {
            query = query.toLowerCase();
            list = atxHint.computeIfAbsent(query, q -> {
                List<String[]> l = new ArrayList<>();
                for( String s : atxSearch.keySet() ) {
                    if( s.contains(q) ) {
                        l.add(atxSearch.get(s));
                    }
                }

                return l;
            });
        }

        return list.subList(0, Math.min(max, list.size()));
    }

    private void readServiceList() {
        try {
            try( Stream<Path> files = Files.list(Path.of("data/services")) ) {
                files.forEach(file -> {
                    if( file.getFileName().toString().length() < 6 ) {
                        return;
                    }

                    try {
                        Files.readAllLines(file).forEach(line -> {
                            String[] pair = line.split(" ", 2);
                            serviceList.add(pair);
                            serviceMap.put(pair[0], pair[1]);
                            String key = pair[0] + " " + pair[1];
                            serviceSearch.put(key.toLowerCase(), pair);
                        });
                    } catch( IOException e ) {
                        e.printStackTrace();
                        System.exit(1);
                    }
                });
            }
        } catch( IOException e ) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void readATXList() {
        try {
            try( Stream<Path> files = Files.list(Path.of("data/atx")) ) {
                files.forEach(file -> {
                    if( file.getFileName().toString().length() < 6 ) {
                        return;
                    }

                    try {
                        Files.readAllLines(file).forEach(line -> {
                            String[] pair = line.split(" ", 3);
                            atxList.add(pair);
                            atxMap.put(pair[0], pair[2]);
                            String key = pair[0] + " " + pair[2];
                            atxSearch.put(key.toLowerCase(), new String[] {pair[0], pair[2]});
                        });
                    } catch( IOException e ) {
                        e.printStackTrace();
                        System.exit(1);
                    }
                });
            }
        } catch( IOException e ) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void readCriteriaList() {
        try {
            try( Stream<Path> files = Files.list(Path.of("data/criteria")) ) {
                files.forEach(file -> {
                    try( BufferedReader reader = Files.newBufferedReader(file) ) {
                        Criteria c = new Criteria();
                        c.ref = reader.readLine();

                        String[] diagList = reader.readLine().split(";");

                        c.items = new ArrayList<>();
                        while( true ) {
                            String item = reader.readLine();
                            if( item == null )
                                break;
                            c.items.add(item);
                        }

                        for( String diag : diagList ) {
                            if( diagToCriteria.put(diag, c) != null ) {
                                System.err.println("Already has criteria for " + diag);
                                System.exit(1);
                            }
                        }

                    } catch( IOException e ) {
                        e.printStackTrace();
                        System.exit(1);
                    }
                });
            }
        } catch( IOException e ) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void readDiagServList() {
        try {
            try( Stream<Path> files = Files.list(Path.of("data/diag-service")) ) {
                files.forEach(file -> {
                    try {
                        String diag = file.getFileName().toString();
                        diag = diag.substring(0, diag.lastIndexOf('.'));
                        List<String> servList = Files.readAllLines(file);
                        Map<String, String> servMap = new HashMap<>();
                        for( String serv : servList ) {
                            String servTitle = serviceMap.get(serv);
                            if( servTitle != null ) {
                                servMap.put(serv, servTitle);
                            }
                        }

                        diagServiceMap.put(diag, servMap);
                    } catch( IOException e ) {
                        e.printStackTrace();
                        System.exit(1);
                    }
                });
            }
        } catch( IOException e ) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void readTreatmentList() {
        try {
            try( Stream<Path> files = Files.list(Path.of("data/treatment")) ) {
                files.forEach(file -> {
                    try {
                        String pat = file.getFileName().toString();
                        pat = pat.substring(0, pat.lastIndexOf('.'));

                        Patient p = patientMap.get(pat);
                        List<Procedure> procList = new ArrayList<>();
                        for( String line : Files.readAllLines(file) ) {
                            if( line.startsWith("#") || line.isEmpty() )
                                continue;

                            String[] fields = line.split("\\s*/\\s*");
                            Procedure proc = new Procedure();
                            proc.code = fields[0];
                            proc.service = serviceMap.containsKey(fields[0]);
                            proc.done = fields[1].equals("1");
                            proc.criteria = Integer.parseInt(fields[2]);
                            procList.add(proc);
                        }

                        p.treatment = procList;
                    } catch( IOException e ) {
                        e.printStackTrace();
                        System.exit(1);
                    }
                });
            }
        } catch( IOException e ) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void readDiagnoseList() {
        try {
            Files.readAllLines(Path.of("data/diag/I.txt")).forEach(line -> {
                String[] pair = line.split(" ", 2);
                diagList.add(pair);
                diagMap.put(pair[0], pair[1]);
            });
        } catch( IOException e ) {
            e.printStackTrace();
            System.exit(1);
        }
    }


    private void readDoctorList() {
        try {
            Files.readAllLines(Path.of("data/objects/doctors.txt")).forEach(line -> {
                if( line.startsWith("#") )
                    return;

                String[] fields = line.split("\\s*/\\s*");
                Doctor d = new Doctor();
                int i = 0;
                d.id = fields[i++];
                d.lastName = fields[i++];
                d.firstName = fields[i++];
                d.middleName = fields[i++];
                d.department = fields[i++];
                doctorList.add(d);
                doctorMap.put(d.id, d);
            });
        } catch( IOException e ) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void readPatientList() {
        try {
            Files.readAllLines(Path.of("data/objects/patients.txt")).forEach(line -> {
                if( line.startsWith("#") )
                    return;

                String[] fields = line.split("\\s*/\\s*");
                Patient p = new Patient();
                int i = 0;
                p.id = fields[i++];
                p.lastName = fields[i++];
                p.firstName = fields[i++];
                p.middleName = fields[i++];
                p.sex = fields[i++];
                p.age = fields[i++];
                p.docId = fields[i++];
                p.diagId = fields[i++];
                p.startDate = fields[i++];
                p.endDate = fields[i++];
                patientList.add(p);
                patientMap.put(p.id, p);
            });
        } catch( IOException e ) {
            e.printStackTrace();
            System.exit(1);
        }
    }


    private static class Criteria {
        public String ref;
        public List<String> items;
    }

}
