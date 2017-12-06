package implementation;

import schema.Campus;
import schema.TimeSlot;
import schema.UdpPacket;

import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class DataHolder {
    public Campus campus = null;
    public List<Campus> campuses;
    HashMap<String, HashMap<Integer, List<String>>> students;
    List<String> admins;
    HashMap<String, HashMap<Integer, List<TimeSlot>>> roomRecords;
    HashMap<Integer, UdpPacket> packetHashMap = new HashMap<>();
    int lastServedPacket;

    public DataHolder() {
        this.campuses = new ArrayList<>();
        this.students = new HashMap<>();
        this.admins = new ArrayList<>();
        this.roomRecords = new HashMap<>();
        this.lastServedPacket = -1;
    }

    int getUdpPort(String code) {
        int port = -1;
        for (Campus item : this.campuses) {
            if (item.getCode().equalsIgnoreCase(code)) {
                port = item.getUdpPort();
                break;
            }
        }

        return port;
    }

    byte[] serialize(Object obj) throws IOException {
        try(ByteArrayOutputStream b = new ByteArrayOutputStream()){
            try(ObjectOutputStream o = new ObjectOutputStream(b)){
                o.writeObject(obj);
            }
            return b.toByteArray();
        }
    }

    Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try(ByteArrayInputStream b = new ByteArrayInputStream(bytes)){
            try(ObjectInputStream o = new ObjectInputStream(b)){
                return o.readObject();
            }
        }
    }

    int getWeekOfYear(String date) {
        int week = -1;

        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy");
            Date dateObj = dateFormat.parse(date);
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(dateObj);
            week = calendar.get(Calendar.WEEK_OF_YEAR);
        } catch (ParseException exception) {
            System.out.println("Error parsing the date.\nMessage: " + exception.getMessage());
        }

        return week;
    }

    static final class CREATE_ROOM {
        static final int operation = 0;
        static final String BODY_ROOM_NUMBER = "rn";
        static final String BODY_DATE = "d";
        static final String BODY_LIST_TIME_SLOT = "lts";
    }

    static final class DELETE_ROOM {
        static final int operation = 1;
        static final String BODY_ROOM_NUMBER = "rn";
        static final String BODY_DATE = "d";
        static final String BODY_LIST_TIME_SLOT = "lts";
    }

    static final class RESET_BOOKING {
        static final int operation = 9;
    }

    static final class VALIDATE_USER {
        static final int operation = 2;
        static final String BODY_ID = "id";
        static final String BODY_CHOICE = "c";
    }

    static final class BOOK_ROOM {
        static final int operation = 3;
        static final String BODY_CAMPUS_CODE = "c";
        static final String BODY_ROOM_NUMBER = "rn";
        static final String BODY_DATE = "d";
        static final String BODY_TIME_SLOT = "ts";
        static final String BODY_STUDENT_ID = "id";
    }

    static final class GET_TIME_SLOTS {
        static final int operation = 4;
        static final String BODY_DATE = "d";
        static final String BODY_FLAG = "f";
    }

    static final class CANCEL_BOOKING {
        static final int operation = 5;
        static final String BODY_STUDENT_ID = "sId";
        static final String BODY_BOOKING_ID = "bId";
    }

    static final class CHANGE_BOOKING {
        static final int operation = 6;
        static final String BODY_BOOKING_ID = "bId";
        static final String BODY_CAMPUS_CODE = "c";
        static final String BODY_ROOM_NUMBER = "rn";
        static final String BODY_TIME_SLOT = "ts";
        static final String BODY_STUDENT_ID = "sId";
        static final String BODY_DATE = "d";
    }

    // when the replica is requesting data to replica manager
    static final class IMPORT_DATA {
        static final int operation = 7;
        static final String BODY_CODE = "c";
    }

    // when the replica manager requests the data
    static final class EXPORT_DATA {
        static final int operation = 8;
    }
}
