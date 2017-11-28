package schema;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

// holds the data representation that can be passed amongst the servers.

public class Student implements Serializable {
    private String studentId;
    public List<String> bookingIds;

    public Student(String studentId) {
        this.studentId = studentId;
        this.bookingIds = new ArrayList<>();
    }

    public String getStudentId() {
        return this.studentId;
    }
}
