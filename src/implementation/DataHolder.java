package implementation;

import schema.Campus;
import schema.Student;
import schema.TimeSlot;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;

public class DataHolder {
    public Campus campus = null;
    public List<Campus> campuses;
    Hashtable<String, Student> students;
    List<String> admins;
    HashMap<String, HashMap<Integer, List<TimeSlot>>> roomRecords;

    public DataHolder() {
        campuses = new ArrayList<>();
        students = new Hashtable<>();
        admins = new ArrayList<>();
        roomRecords = new HashMap<>();
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

    static final class COPY_DATA {
        static final int operation = 7;
        static final String BODY_ROOM_RECORDS = "rr";
    }

    static final class GET_DATA {
        static final int operation = 8;
    }
}
