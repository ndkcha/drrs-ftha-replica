package implementation;

import schema.TimeSlot;
import schema.UdpPacket;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

public class UdpThread implements Runnable {
    private Thread thread;
    private DatagramSocket server;
    private DatagramPacket packet;
    private CampusOperations campusOps;
    private Logger logs;

    public UdpThread(DatagramSocket server, DatagramPacket packet, CampusOperations campusOps, Logger logs) {
        this.server = server;
        this.packet = packet;
        this.campusOps = campusOps;
        this.logs = logs;
    }

    @Override
    public void run() {
        try {
            // parse the packet
            UdpPacket udpPacket = (UdpPacket) deserialize(this.packet.getData());

            // prepare for the response
            byte[] outgoing;
            DatagramPacket res;

            // perform actions
            switch (udpPacket.operation) {
                case CampusOperations.TOTAL_TIMESLOT.OP_CODE:
                    outgoing = this.totalAvailableTimeSlots(udpPacket.body);
                    break;
                case CampusOperations.BOOK_OTHER_SERVER.OP_CODE:
                    outgoing = this.bookRoomFromOtherCampus(udpPacket.body);
                    break;
                case CampusOperations.CANCEL_OTHER_SERVER.OP_CODE:
                    outgoing = this.cancelBookingFromOtherCampus(udpPacket.body);
                    break;
                case CampusOperations.DELETE_BOOKING.OP_CODE:
                    outgoing = this.deleteBooking(udpPacket.body);
                    break;
                case DataHolder.CREATE_ROOM.operation:
                    outgoing = this.createRoom(udpPacket.body);
                    break;
                case DataHolder.DELETE_ROOM.operation:
                    outgoing = this.deleteRoom(udpPacket.body);
                    break;
                case DataHolder.RESET_BOOKING.operation:
                    outgoing = this.serialize(this.campusOps.resetBookings());
                    break;
                case DataHolder.VALIDATE_USER.operation:
                    outgoing = this.validateUser(udpPacket.body);
                    break;
                case DataHolder.BOOK_ROOM.operation:
                    outgoing = this.bookRoom(udpPacket.body);
                    break;
                case DataHolder.GET_TIME_SLOTS.operation:
                    outgoing = this.getAvailableTimeSlots(udpPacket.body);
                    break;
                case DataHolder.CANCEL_BOOKING.operation:
                    outgoing = this.cancelBooking(udpPacket.body);
                    break;
                case DataHolder.CHANGE_BOOKING.operation:
                    outgoing = this.changeBooking(udpPacket.body);
                    break;
                case DataHolder.COPY_DATA.operation:
                    this.copyData(udpPacket.body);
                    return;
                case DataHolder.GET_DATA.operation:
                    outgoing = this.getData();
                    break;
                default:
                    outgoing = serialize("Error");
                    logs.warning("Operation not found!");
                    break;
            }

            // make response and send
            res = (udpPacket.operation < 10) ? (new DatagramPacket(outgoing, outgoing.length, this.packet.getAddress(), this.packet.getPort())) : (new DatagramPacket(outgoing, outgoing.length, InetAddress.getByName("192.168.1.3"), udpPacket.fePort));
            this.server.send(res);
        } catch (IOException ioe) {
            logs.warning("Error reading the packet.\nMessage: " + ioe.getMessage());
        } catch (ClassNotFoundException e) {
            logs.warning("Error parsing the packet.\nMessage: " + e.getMessage());
        }
    }

    private byte[] totalAvailableTimeSlots(HashMap<String, Object> body) throws IOException {
        String date = (String) body.get(CampusOperations.TOTAL_TIMESLOT.BODY_DATE);
        int total = this.campusOps.totalAvailableTimeSlots(date);
        return this.serialize(total);
    }

    private byte[] bookRoomFromOtherCampus(HashMap<String, Object> body) throws IOException {
        String studentId = (String) body.get(CampusOperations.BOOK_OTHER_SERVER.BODY_STUDENT_ID);
        int roomNo = (int) body.get(CampusOperations.BOOK_OTHER_SERVER.BODY_ROOM_NO);
        String date = (String) body.get(CampusOperations.BOOK_OTHER_SERVER.BODY_DATE);
        TimeSlot timeSlot = (TimeSlot) body.get(CampusOperations.BOOK_OTHER_SERVER.BODY_TIME_SLOT);
        String bookingId = this.campusOps.bookRoomFromOtherCampus(studentId, roomNo, date, timeSlot);
        return this.serialize(bookingId);
    }

    private byte[] cancelBookingFromOtherCampus(HashMap<String, Object> body) throws IOException {
        String bookingId = (String) body.get(CampusOperations.CANCEL_OTHER_SERVER.BODY_BOOKING_ID);
        boolean success = this.campusOps.cancelBookingFromOtherCampus(bookingId);
        return this.serialize(success);
    }

    private byte[] deleteBooking(HashMap<String, Object> body) throws IOException {
        String bookingId = (String) body.get(CampusOperations.DELETE_BOOKING.BODY_BOOKING_ID);
        String studentId = (String) body.get(CampusOperations.DELETE_BOOKING.BODY_STUDENT_ID);
        boolean success = this.campusOps.deleteBooking(studentId, bookingId);
        return this.serialize(success);
    }

    @SuppressWarnings (value="unchecked")
    private byte[] createRoom(HashMap<String, Object> body) throws IOException {
        int roomNumber = (int) body.get(DataHolder.CREATE_ROOM.BODY_ROOM_NUMBER);
        String date = (String) body.get(DataHolder.CREATE_ROOM.BODY_DATE);
        List<TimeSlot> timeSlots = (List<TimeSlot>) body.get(DataHolder.CREATE_ROOM.BODY_LIST_TIME_SLOT);
        boolean success = this.campusOps.createRoom(date, roomNumber, timeSlots);
        return this.serialize(success);
    }

    @SuppressWarnings (value="unchecked")
    private byte[] deleteRoom(HashMap<String, Object> body) throws IOException {
        int roomNumber = (int) body.get(DataHolder.DELETE_ROOM.BODY_ROOM_NUMBER);
        String date = (String) body.get(DataHolder.DELETE_ROOM.BODY_DATE);
        List<TimeSlot> timeSlots = (List<TimeSlot>) body.get(DataHolder.DELETE_ROOM.BODY_LIST_TIME_SLOT);
        boolean success = this.campusOps.deleteRoom(date, roomNumber, timeSlots);
        return this.serialize(success);
    }

    private byte[] validateUser(HashMap<String, Object> body) throws IOException {
        String id = (String) body.get(DataHolder.VALIDATE_USER.BODY_ID);
        int choice = (int) body.get(DataHolder.VALIDATE_USER.BODY_CHOICE);
        boolean success = this.campusOps.validateUser(id, choice);
        return this.serialize(success);
    }

    private byte[] bookRoom(HashMap<String, Object> body) throws IOException {
        String code = (String) body.get(DataHolder.BOOK_ROOM.BODY_CAMPUS_CODE);
        int roomNumber = (int) body.get(DataHolder.BOOK_ROOM.BODY_ROOM_NUMBER);
        String date = (String) body.get(DataHolder.BOOK_ROOM.BODY_DATE);
        TimeSlot timeSlot = (TimeSlot) body.get(DataHolder.BOOK_ROOM.BODY_TIME_SLOT);
        String studentId = (String) body.get(DataHolder.BOOK_ROOM.BODY_STUDENT_ID);
        String bookingId =  this.campusOps.bookRoom(studentId, code, date, roomNumber, timeSlot);
        return this.serialize(bookingId);
    }

    private byte[] getAvailableTimeSlots(HashMap<String, Object> body) throws IOException {
        String date = (String) body.get(DataHolder.GET_TIME_SLOTS.BODY_DATE);
        String availableSlots = this.campusOps.getAvailableTimeSlots(date);
        return this.serialize(availableSlots);
    }

    private byte[] cancelBooking(HashMap<String, Object> body) throws IOException {
        String studentId = (String) body.get(DataHolder.CANCEL_BOOKING.BODY_STUDENT_ID);
        String bookingId = (String) body.get(DataHolder.CANCEL_BOOKING.BODY_BOOKING_ID);
        boolean success = this.campusOps.cancelBooking(studentId, bookingId);
        return this.serialize(success);
    }

    private byte[] changeBooking(HashMap<String, Object> body) throws IOException {
        String bookingId = (String) body.get(DataHolder.CHANGE_BOOKING.BODY_BOOKING_ID);
        String code = (String) body.get(DataHolder.CHANGE_BOOKING.BODY_CAMPUS_CODE);
        int roomNumber = (int) body.get(DataHolder.CHANGE_BOOKING.BODY_ROOM_NUMBER);
        TimeSlot timeSlot = (TimeSlot) body.get(DataHolder.CHANGE_BOOKING.BODY_TIME_SLOT);
        String date = (String) body.get(DataHolder.CHANGE_BOOKING.BODY_DATE);
        String newBookingId = this.campusOps.changeBooking(bookingId, code, date, roomNumber, timeSlot);
        return this.serialize(newBookingId);
    }

    private void copyData(HashMap<String, Object> body) throws IOException {

    }

    private byte[] getData() throws IOException {
        return this.serialize("");
    }

    public void start() {
        logs.info("One in coming connection. Forking a thread.");
        if (thread == null) {
            thread = new Thread(this, "Udp Process");
            thread.start();
        }
    }

    private byte[] serialize(Object obj) throws IOException {
        try(ByteArrayOutputStream b = new ByteArrayOutputStream()){
            try(ObjectOutputStream o = new ObjectOutputStream(b)){
                o.writeObject(obj);
            }
            return b.toByteArray();
        }
    }

    private Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try(ByteArrayInputStream b = new ByteArrayInputStream(bytes)){
            try(ObjectInputStream o = new ObjectInputStream(b)){
                return o.readObject();
            }
        }
    }
}

