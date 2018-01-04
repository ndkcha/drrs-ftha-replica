import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import implementation.DataHolder;
import implementation.DataHolder.BOOK_ROOM;
import implementation.DataHolder.CANCEL_BOOKING;
import implementation.DataHolder.CHANGE_BOOKING;
import implementation.DataHolder.DELETE_ROOM;
import implementation.DataHolder.GET_TIME_SLOTS;
import implementation.DataHolder.VALIDATE_USER;
import schema.TimeSlot;
import schema.UdpPacket;

public class Endpoint implements Runnable {
	private Thread thread = null;
	private String tName;
	public static Queue<UdpPacket> holdBack;

	static {
		holdBack = new LinkedList<UdpPacket>();
	}

	public Endpoint(String tName) {
		this.tName = tName;
	}

	public void start() {
		if (thread == null) {
			thread = new Thread(this, tName);
			thread.run();
		}
	}
	
	public static void main(String[] args) {
//		Endpoint listener = new Endpoint("LISTENER");
//		listener.start();
//		Endpoint executor = new Endpoint("EXCECUTOR");
//		executor.start();
		
		MulticastSocket socket = null;
		try {
			socket = new MulticastSocket(5000);
			socket.joinGroup(InetAddress.getByName("224.0.0.5"));

			byte[] buffer = new byte[1000];
			while (true) {
				DatagramPacket request = new DatagramPacket(buffer, buffer.length);
				socket.receive(request);
				System.out.println("Request came in here");
				Object recievedReqSerialized = deserialize(request.getData());
				UdpPacket packet = (UdpPacket) recievedReqSerialized;
				holdBack.add(packet);
				execute(packet);
				System.out.println(String.valueOf(holdBack.isEmpty()) + " : isHoldBackEmpty");
			}
		} catch (Exception exception) {
			exception.printStackTrace();
		}
	}

	@Override
	public void run() {
		System.out.println(tName);
		if (this.tName.equalsIgnoreCase("EXCECUTOR")) {
			try {
				int port = 0;
				int operation = -1;
				int sequence = -1;
				String campusCode;
				HashMap<String, Object> body = new HashMap<String, Object>();
				while (true) {
//					System.out.println(String.valueOf(holdBack.isEmpty()) + " : isHoldBackEmpty");
					if (!holdBack.isEmpty()) {
						UdpPacket packet = holdBack.peek();
						operation = packet.operation;
						port = packet.fePort;
						sequence = packet.sequence;
						body = packet.body;
						campusCode = packet.campus;
						UdpPacket reuestToSend;
						int roomNo, choice;
						String date;
						String time;
						String[] parseTime;
						List<TimeSlot> timeSlots = new ArrayList<>();
						TimeSlot timeSlot;
						HashMap<String, Object> requestData;
						String studentId;
						String campus = "";
						String bookingId;
						String code = "";
						switch (operation) {
						case DataHolder.CREATE_ROOM.operation:
							roomNo = (Integer) body.get(DataHolder.CREATE_ROOM.BODY_ROOM_NUMBER);
							date = (String) body.get(DataHolder.CREATE_ROOM.BODY_DATE);
							time = (String) body.get(DataHolder.CREATE_ROOM.BODY_LIST_TIME_SLOT);
							campus = (String) body.get(DataHolder.BODY_USER_ID);
							timeSlot = new TimeSlot(time);
							timeSlots.add(timeSlot);
							requestData = new HashMap<String, Object>();
							requestData.put(DataHolder.CREATE_ROOM.BODY_DATE, date);
							requestData.put(DataHolder.CREATE_ROOM.BODY_ROOM_NUMBER, roomNo);
							requestData.put(DataHolder.CREATE_ROOM.BODY_LIST_TIME_SLOT, timeSlots);
							reuestToSend = new UdpPacket(operation, requestData, sequence, port);
							break;
						case DataHolder.DELETE_ROOM.operation:
							roomNo = (Integer) body.get(DELETE_ROOM.BODY_ROOM_NUMBER);
							date = (String) body.get(DELETE_ROOM.BODY_DATE);
							time = (String) body.get(DELETE_ROOM.BODY_LIST_TIME_SLOT);
							campus = (String) body.get(DataHolder.BODY_USER_ID);
							timeSlot = new TimeSlot(time);
							timeSlots.add(timeSlot);
							requestData = new HashMap<String, Object>();
							requestData.put(DELETE_ROOM.BODY_DATE, date);
							requestData.put(DELETE_ROOM.BODY_ROOM_NUMBER, roomNo);
							requestData.put(DELETE_ROOM.BODY_LIST_TIME_SLOT, timeSlots);
							reuestToSend = new UdpPacket(operation, requestData, sequence, port);
							break;
						case DataHolder.VALIDATE_USER.operation:
							campus = (String) body.get(DataHolder.VALIDATE_USER.BODY_ID);
							choice = (int) body.get(DataHolder.VALIDATE_USER.BODY_CHOICE);
							requestData = new HashMap<String, Object>();
							requestData.put(VALIDATE_USER.BODY_ID, campus);
							requestData.put(VALIDATE_USER.BODY_CHOICE, choice);
							reuestToSend = new UdpPacket(operation, requestData, sequence, port);
							break;
						case DataHolder.BOOK_ROOM.operation:
							roomNo = (Integer) body.get(BOOK_ROOM.BODY_ROOM_NUMBER);
							date = (String) body.get(BOOK_ROOM.BODY_DATE);
							time = (String) body.get(BOOK_ROOM.BODY_TIME_SLOT);
							parseTime = time.split("-");
							studentId = (String) body.get(BOOK_ROOM.BODY_STUDENT_ID);
							code = (String) body.get(BOOK_ROOM.BODY_CAMPUS_CODE);
							timeSlot = new TimeSlot(time);
							campus = studentId;
							requestData = new HashMap<String, Object>();
							requestData.put(BOOK_ROOM.BODY_CAMPUS_CODE, code);
							requestData.put(BOOK_ROOM.BODY_DATE, date);
							requestData.put(BOOK_ROOM.BODY_ROOM_NUMBER, roomNo);
							requestData.put(BOOK_ROOM.BODY_STUDENT_ID, studentId);
							requestData.put(BOOK_ROOM.BODY_TIME_SLOT, timeSlot);
							reuestToSend = new UdpPacket(operation, requestData, sequence, port);
							break;
						case DataHolder.GET_TIME_SLOTS.operation:
							date = (String) body.get(GET_TIME_SLOTS.BODY_DATE);
							campus = (String) body.get(DataHolder.BODY_USER_ID);
							int flag = 1;
							requestData = new HashMap<String, Object>();
							requestData.put(GET_TIME_SLOTS.BODY_DATE, date);
							requestData.put(GET_TIME_SLOTS.BODY_FLAG, new Integer(1));
							reuestToSend = new UdpPacket(operation, requestData, sequence, port);
							break;
						case DataHolder.CANCEL_BOOKING.operation:
							bookingId = (String) body.get(CANCEL_BOOKING.BODY_BOOKING_ID);
							studentId = (String) body.get(CANCEL_BOOKING.BODY_STUDENT_ID);
							campus = studentId;
							requestData = new HashMap<String, Object>();
							requestData.put(CANCEL_BOOKING.BODY_BOOKING_ID, bookingId);
							requestData.put(CANCEL_BOOKING.BODY_STUDENT_ID, studentId);
							reuestToSend = new UdpPacket(operation, requestData, sequence, port);
							break;
						case DataHolder.CHANGE_BOOKING.operation:
							roomNo = (Integer) body.get(CHANGE_BOOKING.BODY_ROOM_NUMBER);
							date = (String) body.get(CHANGE_BOOKING.BODY_DATE);
							time = (String) body.get(CHANGE_BOOKING.BODY_TIME_SLOT);
							parseTime = time.split("-");
							studentId = (String) body.get(CHANGE_BOOKING.BODY_STUDENT_ID);
							code = (String) body.get(CHANGE_BOOKING.BODY_CAMPUS_CODE);
							bookingId = (String) body.get(CHANGE_BOOKING.BODY_BOOKING_ID);
							timeSlot = new TimeSlot(time);
							campus = studentId;
							requestData = new HashMap<String, Object>();
							requestData.put(CHANGE_BOOKING.BODY_CAMPUS_CODE, code);
							requestData.put(CHANGE_BOOKING.BODY_DATE, date);
							requestData.put(CHANGE_BOOKING.BODY_ROOM_NUMBER, roomNo);
							requestData.put(CHANGE_BOOKING.BODY_STUDENT_ID, studentId);
							requestData.put(CHANGE_BOOKING.BODY_TIME_SLOT, timeSlot);
							requestData.put(CHANGE_BOOKING.BODY_BOOKING_ID, bookingId);
							reuestToSend = new UdpPacket(operation, requestData, sequence, port);
							break;
						default:
							reuestToSend = new UdpPacket(-1, new HashMap<>());
							break;
						}

						int portToForward = 0000;
						if (campus.contains("DVL")) {
							portToForward = 8022;
						} else if (campus.contains("KKL")) {
							portToForward = 8032;
						} else if (campus.contains("WST")) {
							portToForward = 8042;
						}
						System.out.println(campus);
						System.out.println(String.valueOf(portToForward));
						DatagramSocket aSocket = new DatagramSocket();
						byte dataToSend[] = serialize(reuestToSend);
						InetAddress host = InetAddress.getByName("localhost");
						DatagramPacket requestSent = new DatagramPacket(dataToSend, dataToSend.length, host,
								portToForward);
						aSocket.send(requestSent);
						byte[] response = new byte[1000];
						DatagramPacket reply = new DatagramPacket(response, response.length);
						aSocket.receive(reply);

						DatagramSocket aSocket1 = new DatagramSocket();
						InetAddress host1 = InetAddress.getByName("132.205.93.42");
						DatagramPacket requestSent1 = new DatagramPacket(reply.getData(), reply.getData().length, host1,
								port);
						aSocket.send(requestSent1);
						holdBack.poll();
					}
				}
			} catch (Exception exception) {
				exception.printStackTrace();
			}
		} else {
			MulticastSocket socket = null;
			try {
				socket = new MulticastSocket(5000);
				socket.joinGroup(InetAddress.getByName("224.0.0.5"));

				byte[] buffer = new byte[1000];
				while (true) {
					DatagramPacket request = new DatagramPacket(buffer, buffer.length);
					socket.receive(request);
					System.out.println("Request came in here");
					Object recievedReqSerialized = deserialize(request.getData());
					UdpPacket packet = (UdpPacket) recievedReqSerialized;
					holdBack.add(packet);
					System.out.println(String.valueOf(holdBack.isEmpty()) + " : isHoldBackEmpty");
				}
			} catch (Exception exception) {
				exception.printStackTrace();
			}
		}

	}

	public static byte[] serialize(Object obj) throws IOException {
		try (ByteArrayOutputStream b = new ByteArrayOutputStream()) {
			try (ObjectOutputStream o = new ObjectOutputStream(b)) {
				o.writeObject(obj);
			}
			return b.toByteArray();
		}
	}

	public static Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
		try (ByteArrayInputStream b = new ByteArrayInputStream(bytes)) {
			try (ObjectInputStream o = new ObjectInputStream(b)) {
				return o.readObject();
			}
		}
	}
	
	public static void execute(UdpPacket packet){

		try {
			int port = 0;
			int operation = -1;
			int sequence = -1;
			String campusCode;
			HashMap<String, Object> body = new HashMap<String, Object>();
//			while (true) {
////				System.out.println(String.valueOf(holdBack.isEmpty()) + " : isHoldBackEmpty");
//				if (!holdBack.isEmpty()) {
//					UdpPacket packet = holdBack.peek();
					operation = packet.operation;
					port = packet.fePort;
					sequence = packet.sequence;
					body = packet.body;
					campusCode = packet.campus;
					UdpPacket reuestToSend;
					int roomNo, choice;
					String date;
					String time;
					String[] parseTime;
					List<TimeSlot> timeSlots = new ArrayList<>();
					TimeSlot timeSlot;
					HashMap<String, Object> requestData;
					String studentId;
					String campus = "";
					String bookingId;
					String code = "";
					switch (operation) {
					case DataHolder.CREATE_ROOM.operation:
						roomNo = (Integer) body.get(DataHolder.CREATE_ROOM.BODY_ROOM_NUMBER);
						date = (String) body.get(DataHolder.CREATE_ROOM.BODY_DATE);
						time = (String) body.get(DataHolder.CREATE_ROOM.BODY_LIST_TIME_SLOT);
						campus = (String) body.get(DataHolder.BODY_USER_ID);
						timeSlot = new TimeSlot(time);
						timeSlots.add(timeSlot);
						requestData = new HashMap<String, Object>();
						requestData.put(DataHolder.CREATE_ROOM.BODY_DATE, date);
						requestData.put(DataHolder.CREATE_ROOM.BODY_ROOM_NUMBER, roomNo);
						requestData.put(DataHolder.CREATE_ROOM.BODY_LIST_TIME_SLOT, timeSlots);
						reuestToSend = new UdpPacket(operation, requestData, sequence, port);
						break;
					case DataHolder.DELETE_ROOM.operation:
						roomNo = (Integer) body.get(DELETE_ROOM.BODY_ROOM_NUMBER);
						date = (String) body.get(DELETE_ROOM.BODY_DATE);
						time = (String) body.get(DELETE_ROOM.BODY_LIST_TIME_SLOT);
						campus = (String) body.get(DataHolder.BODY_USER_ID);
						timeSlot = new TimeSlot(time);
						timeSlots.add(timeSlot);
						requestData = new HashMap<String, Object>();
						requestData.put(DELETE_ROOM.BODY_DATE, date);
						requestData.put(DELETE_ROOM.BODY_ROOM_NUMBER, roomNo);
						requestData.put(DELETE_ROOM.BODY_LIST_TIME_SLOT, timeSlots);
						reuestToSend = new UdpPacket(operation, requestData, sequence, port);
						break;
					case DataHolder.VALIDATE_USER.operation:
						campus = (String) body.get(DataHolder.VALIDATE_USER.BODY_ID);
						choice = (int) body.get(DataHolder.VALIDATE_USER.BODY_CHOICE);
						requestData = new HashMap<String, Object>();
						requestData.put(VALIDATE_USER.BODY_ID, campus);
						requestData.put(VALIDATE_USER.BODY_CHOICE, choice);
						reuestToSend = new UdpPacket(operation, requestData, sequence, port);
						break;
					case DataHolder.BOOK_ROOM.operation:
						roomNo = (Integer) body.get(BOOK_ROOM.BODY_ROOM_NUMBER);
						date = (String) body.get(BOOK_ROOM.BODY_DATE);
						time = (String) body.get(BOOK_ROOM.BODY_TIME_SLOT);
						parseTime = time.split("-");
						studentId = (String) body.get(BOOK_ROOM.BODY_STUDENT_ID);
						code = (String) body.get(BOOK_ROOM.BODY_CAMPUS_CODE);
						timeSlot = new TimeSlot(time);
						campus = studentId;
						requestData = new HashMap<String, Object>();
						requestData.put(BOOK_ROOM.BODY_CAMPUS_CODE, code);
						requestData.put(BOOK_ROOM.BODY_DATE, date);
						requestData.put(BOOK_ROOM.BODY_ROOM_NUMBER, roomNo);
						requestData.put(BOOK_ROOM.BODY_STUDENT_ID, studentId);
						requestData.put(BOOK_ROOM.BODY_TIME_SLOT, timeSlot);
						reuestToSend = new UdpPacket(operation, requestData, sequence, port);
						break;
					case DataHolder.GET_TIME_SLOTS.operation:
						date = (String) body.get(GET_TIME_SLOTS.BODY_DATE);
						campus = (String) body.get(DataHolder.BODY_USER_ID);
						int flag = 1;
						requestData = new HashMap<String, Object>();
						requestData.put(GET_TIME_SLOTS.BODY_DATE, date);
						requestData.put(GET_TIME_SLOTS.BODY_FLAG, new Integer(1));
						reuestToSend = new UdpPacket(operation, requestData, sequence, port);
						break;
					case DataHolder.CANCEL_BOOKING.operation:
						bookingId = (String) body.get(CANCEL_BOOKING.BODY_BOOKING_ID);
						studentId = (String) body.get(CANCEL_BOOKING.BODY_STUDENT_ID);
						campus = studentId;
						requestData = new HashMap<String, Object>();
						requestData.put(CANCEL_BOOKING.BODY_BOOKING_ID, bookingId);
						requestData.put(CANCEL_BOOKING.BODY_STUDENT_ID, studentId);
						reuestToSend = new UdpPacket(operation, requestData, sequence, port);
						break;
					case DataHolder.CHANGE_BOOKING.operation:
						roomNo = (Integer) body.get(CHANGE_BOOKING.BODY_ROOM_NUMBER);
						date = (String) body.get(CHANGE_BOOKING.BODY_DATE);
						time = (String) body.get(CHANGE_BOOKING.BODY_TIME_SLOT);
						parseTime = time.split("-");
						studentId = (String) body.get(CHANGE_BOOKING.BODY_STUDENT_ID);
						code = (String) body.get(CHANGE_BOOKING.BODY_CAMPUS_CODE);
						bookingId = (String) body.get(CHANGE_BOOKING.BODY_BOOKING_ID);
						timeSlot = new TimeSlot(time);
						campus = studentId;
						requestData = new HashMap<String, Object>();
						requestData.put(CHANGE_BOOKING.BODY_CAMPUS_CODE, code);
						requestData.put(CHANGE_BOOKING.BODY_DATE, date);
						requestData.put(CHANGE_BOOKING.BODY_ROOM_NUMBER, roomNo);
						requestData.put(CHANGE_BOOKING.BODY_STUDENT_ID, studentId);
						requestData.put(CHANGE_BOOKING.BODY_TIME_SLOT, timeSlot);
						requestData.put(CHANGE_BOOKING.BODY_BOOKING_ID, bookingId);
						reuestToSend = new UdpPacket(operation, requestData, sequence, port);
						break;
					default:
						reuestToSend = new UdpPacket(-1, new HashMap<>());
						break;
					}

					int portToForward = 0000;
					if (campus.contains("DVL")) {
						portToForward = 8022;
					} else if (campus.contains("KKL")) {
						portToForward = 8032;
					} else if (campus.contains("WST")) {
						portToForward = 8042;
					}
					System.out.println(campus);
					System.out.println(String.valueOf(portToForward));
					DatagramSocket aSocket = new DatagramSocket();
					byte dataToSend[] = serialize(reuestToSend);
					InetAddress host = InetAddress.getByName("localhost");
					DatagramPacket requestSent = new DatagramPacket(dataToSend, dataToSend.length, host,
							portToForward);
					aSocket.send(requestSent);
					byte[] response = new byte[1000];
					DatagramPacket reply = new DatagramPacket(response, response.length);
					aSocket.receive(reply);

					DatagramSocket aSocket1 = new DatagramSocket();
					InetAddress host1 = InetAddress.getByName("132.205.93.42");
					DatagramPacket requestSent1 = new DatagramPacket(reply.getData(), reply.getData().length, host1,
							port);
					aSocket.send(requestSent1);
					holdBack.poll();
				//}
			//}
		} catch (Exception exception) {
			exception.printStackTrace();
		}
	
	}
}
