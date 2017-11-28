package implementation;

import schema.Campus;
import schema.Student;
import schema.TimeSlot;
import schema.UdpPacket;

import javax.jws.WebMethod;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.*;
import java.util.logging.Logger;

public class CampusOperations {
    private static final Object studentLock = new Object();
    private static final Object roomLock = new Object();

    private Logger logs;
    private DataHolder dataHolder;

    public CampusOperations(Logger logs, DataHolder holder) {
        this.logs = logs;
        this.dataHolder = holder;
    }

    boolean validateUser(String id, int choice) {
        boolean success = (choice == 0) ? (this.dataHolder.admins.indexOf(id) > -1) : (this.dataHolder.students.containsKey(id));
        if (success)
            this.logs.info("Admin with id " + id + " has logged into the system.");
        else
            this.logs.warning("Unknown login detected. Id: " + id);
        return success;
    }

    boolean createRoom(String date, int roomNo, List<TimeSlot> timeSlots) {
        synchronized (roomLock) {
            // date already exists ? update the room : new date
            boolean isDateExists = this.dataHolder.roomRecords.containsKey(date);
            // get the map of rooms of given date
            HashMap<Integer, List<TimeSlot>> room = isDateExists ? this.dataHolder.roomRecords.get(date) : new HashMap<>();
            // room already exists ? update time slots : new room
            boolean isRoomExists = room.containsKey(roomNo);
            List<TimeSlot> slots = (isRoomExists) ? room.get(roomNo) : new ArrayList<>();
            for (TimeSlot inSlot : timeSlots) {
                // room doesn't exit ? add anyways : avoid duplicates
                if (!isRoomExists || (this.indexOfTimeSlot(inSlot.time, slots) < 0))
                    slots.add(inSlot);
            }
            // update room
            room.put(roomNo, slots);
            // update date
            this.dataHolder.roomRecords.put(date, room);

            this.logs.info(isDateExists ? (isRoomExists ? "Time slots have been added to the room." : "New room has been created along with time slots") : "New date has been recorded along with the room and time slots");

            return true;
        }
    }

    // find the duplicate time slots and return their indexes. no duplicates ? return negative.
    private int indexOfTimeSlot(String time, List<TimeSlot> list) {
        for (TimeSlot item : list) {
            if (item.time.equalsIgnoreCase(time))
                return list.indexOf(item);
        }
        return -1;
    }

    boolean deleteRoom(String date, int roomNo, List<TimeSlot> timeSlots) {
        boolean success = false;

        // make sure no one manipulates the data-set
        synchronized (roomLock) {
            // find the date
            if (this.dataHolder.roomRecords.containsKey(date)) {
                HashMap<Integer, List<TimeSlot>> rooms = this.dataHolder.roomRecords.get(date);

                // find the room
                if (rooms.containsKey(roomNo)) {
                    List<TimeSlot> timeSlotList = rooms.get(roomNo);
                    List<Integer> slotsToDelete = new ArrayList<>();

                    for (TimeSlot inTimeSlot : timeSlots) {
                        // find slots to delete and delete them
                        for (TimeSlot item : timeSlotList) {
                            if (item.time.equalsIgnoreCase(inTimeSlot.time)) {
                                int slotIndex = timeSlotList.indexOf(item);

                                // is it already booked ?
                                if (!item.getBookedBy().isEmpty()) {
                                    String code = item.getBookedBy().substring(0, 3).toUpperCase();

                                    // is it own student ? update the count : ask the server to update their count
                                    if (this.dataHolder.students.containsKey(item.getBookedBy())) {
                                        synchronized (studentLock) {
                                            Student student = this.dataHolder.students.get(item.getBookedBy());
                                            student.bookingIds.remove(item.getBookingId());
                                            this.dataHolder.students.put(student.getStudentId(), student);
                                        }
                                    } else {
                                        int port = this.dataHolder.getUdpPort(code);
                                        this.deleteBookingOnOtherServer(item.getBookedBy(), item.getBookingId(), port);
                                    }
                                }

                                slotsToDelete.add(slotIndex);
                            }
                        }

                        for (int slotIndex : slotsToDelete) {
                            timeSlotList.remove(slotIndex);
                        }
                    }

                    rooms.put(roomNo, timeSlotList);
                    this.dataHolder.roomRecords.put(date, rooms);

                    success = true;
                    this.logs.info("The room has successfully been deleted");
                }
            }
        }

        return success;
    }

    boolean resetBookings() {
        // make sure no one else touches the room
        synchronized (roomLock) {
            // iterate through date entries
            for (Map.Entry<String, HashMap<Integer, List<TimeSlot>>> dateEntry : this.dataHolder.roomRecords.entrySet()) {
                String date = dateEntry.getKey();
                HashMap<Integer, List<TimeSlot>> rooms = dateEntry.getValue();

                // iterate through room entries
                for (Map.Entry<Integer, List<TimeSlot>> roomEntry : rooms.entrySet()) {
                    int roomNumber = roomEntry.getKey();
                    List<TimeSlot> timeSlots = roomEntry.getValue();

                    // clear the bookings in all the time slots
                    for (TimeSlot slot : timeSlots) {
                        // booking exists
                        if (!slot.getBookedBy().isEmpty()) {
                            int slotIndex = timeSlots.indexOf(slot);
                            String code = slot.getBookedBy().substring(0, 3).toUpperCase();

                            // is it own student ? update the count : ask the server to update their count
                            if (this.dataHolder.students.containsKey(slot.getBookedBy())) {
                                synchronized (studentLock) {
                                    Student student = this.dataHolder.students.get(slot.getBookedBy());
                                    student.bookingIds.remove(slot.getBookingId());
                                    this.dataHolder.students.put(student.getStudentId(), student);
                                }
                            } else {
                                int port = this.dataHolder.getUdpPort(code);
                                this.deleteBookingOnOtherServer(slot.getBookedBy(), slot.getBookingId(), port);
                            }

                            // clear the room
                            slot.cancelBooking();

                            // update the time-slots
                            timeSlots.set(slotIndex, slot);
                        }
                    }

                    // update the rooms
                    rooms.put(roomNumber, timeSlots);
                }

                // update the date
                this.dataHolder.roomRecords.put(date, rooms);
            }
            this.logs.info("The booking have successfully been reset");
            return true;
        }
    }

    private void deleteBookingOnOtherServer(String studentId, String bookingId, int port) {
        // connect to relevant server
        try {
            DatagramSocket socket = new DatagramSocket();

            // make data object
            HashMap<String, Object> body = new HashMap<>();
            body.put(DELETE_BOOKING.BODY_STUDENT_ID, studentId);
            body.put(DELETE_BOOKING.BODY_BOOKING_ID, bookingId);
            UdpPacket udpPacket = new UdpPacket(DELETE_BOOKING.OP_CODE, body);

            // make packet and send
            byte[] outgoing = this.dataHolder.serialize(udpPacket);
            DatagramPacket outgoingPacket = new DatagramPacket(outgoing, outgoing.length, InetAddress.getByName("localhost"), port);
            socket.send(outgoingPacket);

            // incoming
            byte[] incoming = new byte[1000];
            DatagramPacket incomingPacket = new DatagramPacket(incoming, incoming.length);
            socket.receive(incomingPacket);

            if ((boolean) this.dataHolder.deserialize(incomingPacket.getData()))
                this.logs.info("The booking has been removed from " + studentId);
            else
                this.logs.warning("There has been problem removing the booking from " + studentId);
        } catch (SocketException se) {
            logs.warning("Error creating a client socket for connection to the other server.\nMessage: " + se.getMessage());
        } catch (IOException ioe) {
            logs.warning("Error creating serialized object.\nMessage: " + ioe.getMessage());
        } catch (ClassNotFoundException cne) {
            logs.warning("Error parsing the response.\n Message: " + cne.getMessage());
        }
    }

    // invoke this when room is deleted for that booking.
    boolean deleteBooking(String studentId, String bookingId) {
        synchronized (studentLock) {
            if (this.dataHolder.students.containsKey(studentId)) {

                Student student = this.dataHolder.students.get(studentId);
                student.bookingIds.remove(bookingId);

                this.dataHolder.students.put(studentId, student);
                this.logs.info("The booking with id " + bookingId + " has been removed from student " + studentId);
            } else {
                this.logs.warning("Failed to remove booking " + bookingId + " from student " + studentId);
                return false;
            }
        }
        return true;
    }

    String getAvailableTimeSlots(String date) {
        int total;
        String availableTimeSlots = "";

        for (Campus item : this.dataHolder.campuses) {
            if (item.getCode().equalsIgnoreCase(this.dataHolder.campus.getCode())) {
                availableTimeSlots = availableTimeSlots.concat(this.dataHolder.campus.getCode() + "," + String.valueOf(this.totalAvailableTimeSlots(date)) + ";");
                continue;
            }
            total = this.fetchTotalTimeSlots(date, item.getUdpPort());
            availableTimeSlots = availableTimeSlots.concat(item.getCode() + "," + String.valueOf(total) + ";");
        }

        this.logs.info("The available time slots have been returned to the user.");
        return availableTimeSlots;
    }

    // gets total number of available time slots for a particular campus server.
    int totalAvailableTimeSlots(String date) {
        int total = 0;

        if (!this.dataHolder.roomRecords.containsKey(date))
            return 0;

        HashMap<Integer, List<TimeSlot>> rooms = this.dataHolder.roomRecords.get(date);

        for (Map.Entry<Integer, List<TimeSlot>> entry : rooms.entrySet()) {
            List<TimeSlot> slots = entry.getValue();

            for (TimeSlot item : slots) {
                // already booked ? it's not available
                total += ((item.getBookingId().isEmpty()) ? 1 : 0);
            }
        }

        this.logs.info("The total time slots for " + date + " have been requested and served!");
        return total;
    }

    private int fetchTotalTimeSlots(String date, int udpPort) {
        int total = 0;
        // connect to campus server
        try {
            DatagramSocket socket = new DatagramSocket();

            // make data object
            HashMap<String, Object> body = new HashMap<>();
            body.put(TOTAL_TIMESLOT.BODY_DATE, date);
            UdpPacket udpPacket = new UdpPacket(TOTAL_TIMESLOT.OP_CODE, body);

            // make packet and send
            byte[] outgoing = this.dataHolder.serialize(udpPacket);
            DatagramPacket outgoingPacket = new DatagramPacket(outgoing, outgoing.length, InetAddress.getByName("localhost"), udpPort);
            socket.send(outgoingPacket);

            // incoming
            byte[] incoming = new byte[1000];
            DatagramPacket incomingPacket = new DatagramPacket(incoming, incoming.length);
            socket.receive(incomingPacket);

            total = (int) this.dataHolder.deserialize(incomingPacket.getData());
        } catch (SocketException se) {
            logs.warning("Error creating a client socket for connection to central repository.\nMessage: " + se.getMessage());
        } catch (IOException ioe) {
            logs.warning("Error creating serialized object.\nMessage: " + ioe.getMessage());
        } catch (ClassNotFoundException e) {
            logs.warning("Error parsing the response from central repository.\nMessage: " + e.getMessage());
        }

        return total;
    }

    String bookRoom(String studentId, String code, String date, int roomNumber, TimeSlot timeSlot) {
        String bookingId;
        Student student;

        // no student. no booking.
        if (!this.dataHolder.students.containsKey(studentId))
            return "No student found!";

        student = this.dataHolder.students.get(studentId);

        // super active student. no booking.
        if (student.bookingIds.size() > 2)
            return "Maximum booking limit has been exceeded.";

        if (code.equalsIgnoreCase(this.dataHolder.campus.getCode())) {
            // make sure others don't book it.
            synchronized (roomLock) {
                // no date. no booking.
                if (!this.dataHolder.roomRecords.containsKey(date))
                    return "Incorrect date provided!";
                HashMap<Integer, List<TimeSlot>> room = this.dataHolder.roomRecords.get(date);
                // no room. no booking
                if (!room.containsKey(roomNumber))
                    return "Incorrect room number provided!";
                List<TimeSlot> timeSlots = room.get(roomNumber);
                // get the time slot to book
                TimeSlot slot = null;
                int index = -1;
                for (TimeSlot item : timeSlots) {
                    if (item.time.equalsIgnoreCase(timeSlot.time)) {
                        slot = item;
                        index = timeSlots.indexOf(item);
                        break;
                    }
                }
                // no time slot. no booking.
                if (slot == null)
                    return "Time slot does not exist!";
                // already booked ? no booking.
                if (!slot.getBookedBy().isEmpty())
                    return "Time slot has already been booked by other student.";

                // generate booking id.
                Random random = new Random();
                int num = random.nextInt(10000);
                bookingId = "BKG" + this.dataHolder.campus.getCode().toUpperCase() + String.format("%04d", num);

                // book it.
                slot.bookTimeSlot(studentId, bookingId);

                // update room records
                timeSlots.set(index, slot);
                room.put(roomNumber, timeSlots);
                this.dataHolder.roomRecords.put(date, room);
            }

            synchronized (studentLock) {
                student.bookingIds.add(bookingId);
                this.dataHolder.students.put(studentId, student);
            }

            logs.info("New booking has been created under " + studentId + " with id, " + bookingId);
        } else {
            // get the port of the other campus
            int port = this.dataHolder.getUdpPort(code);
            // book on the other campus
            bookingId = bookRoomOnOtherCampus(studentId, roomNumber, date, timeSlot, port);
            // update the count
            if ((bookingId != null) && (bookingId.startsWith("BKG"))) {
                synchronized (studentLock) {
                    student.bookingIds.add(bookingId);
                    this.dataHolder.students.put(studentId, student);
                }
                logs.info("New booking has been created under " + studentId + " with id, " + bookingId);
            }
        }

        return bookingId;
    }

    private String bookRoomOnOtherCampus(String studentId, int roomNo, String date, TimeSlot slot, int udpPort) {
        String bookingId = null;

        // connect to the other campus
        try {
            DatagramSocket socket = new DatagramSocket();

            // make data object
            HashMap<String, Object> body = new HashMap<>();
            body.put(BOOK_OTHER_SERVER.BODY_STUDENT_ID, studentId);
            body.put(BOOK_OTHER_SERVER.BODY_ROOM_NO, roomNo);
            body.put(BOOK_OTHER_SERVER.BODY_DATE, date);
            body.put(BOOK_OTHER_SERVER.BODY_TIME_SLOT, slot);
            UdpPacket udpPacket = new UdpPacket(BOOK_OTHER_SERVER.OP_CODE, body);

            // make packet and send
            byte[] outgoing = this.dataHolder.serialize(udpPacket);
            DatagramPacket outgoingPacket = new DatagramPacket(outgoing, outgoing.length, InetAddress.getByName("localhost"), udpPort);
            socket.send(outgoingPacket);

            // incoming
            byte[] incoming = new byte[1000];
            DatagramPacket incomingPacket = new DatagramPacket(incoming, incoming.length);
            socket.receive(incomingPacket);

            bookingId = (String) this.dataHolder.deserialize(incomingPacket.getData());

        } catch (SocketException se) {
            logs.warning("Error creating a client socket for connection to the other campus.\nMessage: " + se.getMessage());
        } catch (IOException ioe) {
            logs.warning("Error creating serialized object.\nMessage: " + ioe.getMessage());
        } catch (ClassNotFoundException e) {
            logs.warning("Error parsing the response from the other campus.\nMessage: " + e.getMessage());
        }

        return bookingId;
    }

    String bookRoomFromOtherCampus(String studentId, int roomNumber, String date, TimeSlot timeSlot) {
        String bookingId;
        // make sure others don't book it.
        synchronized (roomLock) {
            // no date. no booking.
            if (!this.dataHolder.roomRecords.containsKey(date))
                return "Incorrect date provided!";
            HashMap<Integer, List<TimeSlot>> room = this.dataHolder.roomRecords.get(date);
            // no room. no booking
            if (!room.containsKey(roomNumber))
                return "Incorrect room number provided!";
            List<TimeSlot> timeSlots = room.get(roomNumber);
            // get the time slot to book
            TimeSlot slot = null;
            int index = -1;
            for (TimeSlot item : timeSlots) {
                if (item.time.equalsIgnoreCase(timeSlot.time)) {
                    slot = item;
                    index = timeSlots.indexOf(item);
                    break;
                }
            }
            // no time slot. no booking.
            if (slot == null)
                return "Time slot does not exist!";
            // already booked ? no booking.
            if (!slot.getBookedBy().isEmpty())
                return "Time slot has already been booked by other student.";

            // generate booking id.
            Random random = new Random();
            int num = random.nextInt(10000);
            bookingId = "BKG" + this.dataHolder.campus.getCode().toUpperCase() + String.format("%04d", num);

            // book it.
            slot.bookTimeSlot(studentId, bookingId);

            // update room records
            timeSlots.set(index, slot);
            room.put(roomNumber, timeSlots);
            this.dataHolder.roomRecords.put(date, room);

            logs.info("New booking has been created under " + studentId + " with id, " + bookingId);
        }

        return bookingId;
    }

    boolean cancelBooking(String studentId, String bookingId) {
        boolean success = false;
        Student student;
        String code = bookingId.substring(3, 6);

        // no student. no cancelling.
        if (!this.dataHolder.students.containsKey(studentId))
            return false;

        student = this.dataHolder.students.get(studentId);

        // own campus
        if (code.equalsIgnoreCase(this.dataHolder.campus.getCode())) {
            // make sure no one else manipulates the records
            synchronized (roomLock) {
                // find the date
                for (Map.Entry<String, HashMap<Integer, List<TimeSlot>>> dateEntry : this.dataHolder.roomRecords.entrySet()) {
                    String date = dateEntry.getKey();
                    HashMap<Integer, List<TimeSlot>> room = dateEntry.getValue();

                    // find the room
                    for (Map.Entry<Integer, List<TimeSlot>> roomEntry : room.entrySet()) {
                        int roomNumber = roomEntry.getKey();
                        List<TimeSlot> timeSlots = roomEntry.getValue();

                        // find the time slot
                        for (TimeSlot item : timeSlots) {
                            if (item.getBookingId().equalsIgnoreCase(bookingId)) {
                                int slotIndex = timeSlots.indexOf(item);

                                item.cancelBooking();

                                // update the data-set
                                timeSlots.set(slotIndex, item);
                                room.put(roomNumber, timeSlots);
                                this.dataHolder.roomRecords.put(date, room);

                                // mark the operation successful
                                success = true;
                                break;
                            }
                        }

                        if (success)
                            break;
                    }

                    if (success)
                        break;
                }
            }
        } else {
            // other campus.
            int port = this.dataHolder.getUdpPort(code);
            success = this.cancelBookingOnOtherCampus(bookingId, port);
        }

        // update the student count
        if (success) {
            synchronized (studentLock) {
                int bookingIndex = student.bookingIds.indexOf(bookingId);
                student.bookingIds.remove(bookingIndex);
                this.dataHolder.students.put(studentId, student);
                logs.info("Booking with id, " + bookingId + " has been cancelled by " + studentId);
            }
        }

        return success;
    }

    String changeBooking(String bookingId, String code, String date, int roomNumber, TimeSlot timeSlot) {
        String studentId, newBookingId;
        Student student = null;

        // find the corresponding student
        for (Map.Entry<String, Student> studentEntry : this.dataHolder.students.entrySet()) {
            if (studentEntry.getValue().bookingIds.indexOf(bookingId) > -1) {
                student = studentEntry.getValue();
                break;
            }
        }

        // no student. it's not my booking.
        if (student == null)
            return "No booking found with id " + bookingId + " at the campus.";

        // extract the student id for the future use
        studentId = student.getStudentId();

        // decrement the student count to ensure the new booking
        synchronized (studentLock) {
            student.bookingIds.remove(bookingId);
            this.dataHolder.students.put(studentId, student);
        }

        // book a new room
        newBookingId = this.bookRoom(studentId, code, date, roomNumber, timeSlot);

        // update the count with old booking id (later on it can be changed based on the success of booking the new room
        synchronized (studentLock) {
            student.bookingIds.add(bookingId);
            this.dataHolder.students.put(studentId, student);
        }

        //  new room booked successfully ? cancel the old booking : the count has already been restored, just return back to client
        if (newBookingId.startsWith("BKG")) {
            if (!this.cancelBooking(studentId, bookingId)) {
                newBookingId = "Could not cancel the old booking.";
                this.cancelBooking(studentId, newBookingId);
            }
        } else {
            this.logs.warning(newBookingId);
            newBookingId = "The booking could not be changed.\n" + newBookingId;
        }

        return newBookingId;
    }

    private boolean cancelBookingOnOtherCampus(String bookingId, int udpPort) {
        boolean success = false;

        // connect to the other campus
        try {
            DatagramSocket socket = new DatagramSocket();

            // make data object
            HashMap<String, Object> body = new HashMap<>();
            body.put(CANCEL_OTHER_SERVER.BODY_BOOKING_ID, bookingId);
            UdpPacket udpPacket = new UdpPacket(CANCEL_OTHER_SERVER.OP_CODE, body);

            // make packet and send
            byte[] outgoing = this.dataHolder.serialize(udpPacket);
            DatagramPacket outgoingPacket = new DatagramPacket(outgoing, outgoing.length, InetAddress.getByName("localhost"), udpPort);
            socket.send(outgoingPacket);

            // incoming
            byte[] incoming = new byte[1000];
            DatagramPacket incomingPacket = new DatagramPacket(incoming, incoming.length);
            socket.receive(incomingPacket);

            success = (boolean) this.dataHolder.deserialize(incomingPacket.getData());

        } catch (SocketException se) {
            logs.warning("Error creating a client socket for connection to the other campus.\nMessage: " + se.getMessage());
        } catch (IOException ioe) {
            logs.warning("Error creating serialized object.\nMessage: " + ioe.getMessage());
        } catch (ClassNotFoundException e) {
            logs.warning("Error parsing the response from the other campus.\nMessage: " + e.getMessage());
        }

        return success;
    }

    boolean cancelBookingFromOtherCampus(String bookingId) {
        boolean success = false;
        // make sure no one else manipulates the records
        synchronized (roomLock) {
            // find the date
            for (Map.Entry<String, HashMap<Integer, List<TimeSlot>>> dateEntry : this.dataHolder.roomRecords.entrySet()) {
                String date = dateEntry.getKey();
                HashMap<Integer, List<TimeSlot>> room = dateEntry.getValue();

                // find the room
                for (Map.Entry<Integer, List<TimeSlot>> roomEntry : room.entrySet()) {
                    int roomNumber = roomEntry.getKey();
                    List<TimeSlot> timeSlots = roomEntry.getValue();

                    // find the time slot
                    for (TimeSlot item : timeSlots) {
                        if (item.getBookingId().equalsIgnoreCase(bookingId)) {
                            int slotIndex = timeSlots.indexOf(item);

                            item.cancelBooking();

                            // update the data-set
                            timeSlots.set(slotIndex, item);
                            room.put(roomNumber, timeSlots);
                            this.dataHolder.roomRecords.put(date, room);

                            // mark the operation successful
                            success = true;
                            break;
                        }
                    }

                    if (success)
                        break;
                }

                if (success)
                    break;
            }
        }
        return success;
    }

    static abstract class TOTAL_TIMESLOT {
        static final int OP_CODE = 10;
        static final String BODY_DATE = "dt";
    }

    static abstract class BOOK_OTHER_SERVER {
        static final int OP_CODE = 11;
        static final String BODY_STUDENT_ID = "sI";
        static final String BODY_ROOM_NO = "rN";
        static final String BODY_DATE = "dt";
        static final String BODY_TIME_SLOT = "ts";
    }

    static abstract class CANCEL_OTHER_SERVER {
        static final int OP_CODE = 12;
        static final String BODY_BOOKING_ID = "bI";
    }

    static abstract class DELETE_BOOKING {
        static final int OP_CODE = 13;
        static final String BODY_BOOKING_ID = "bI";
        static final String BODY_STUDENT_ID = "sI";
    }
}
