package schema;

import java.io.Serializable;

public class TimeSlot implements Serializable {
    public String time = "";
    private String bookedBy = "";
    private String bookingId = "";

    public TimeSlot() {
        this.time = "";
        this.bookingId = "";
        this.bookedBy = "";
    }

    public TimeSlot(String time) {
        this.time = time;
        this.bookedBy = "";
        this.bookingId = "";
    }

    public void bookTimeSlot(String bookedBy, String bookingId) {
        this.bookedBy = bookedBy;
        this.bookingId = bookingId;
    }

    public String getBookedBy() {
        return this.bookedBy;
    }

    public String getBookingId() {
        return this.bookingId;
    }

    public void cancelBooking() {
        this.bookedBy = "";
        this.bookingId = "";
    }
}
