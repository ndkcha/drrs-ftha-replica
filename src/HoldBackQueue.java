import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.HashMap;

import implementation.ServeHoldBackThread;
import schema.UdpPacket;

public class HoldBackQueue {
	public static final Object queueLock = new Object();
	
	public static void main(String[] args) {
		HashMap<Integer, UdpPacket> packetMap = new HashMap<>();
		int lastServedQueue = -1;
		
		ServeHoldBackThread holdBackThread = new ServeHoldBackThread(packetMap, lastServedQueue);
		holdBackThread.start();
		
		// listen to the port
		try {
			MulticastSocket socket = new MulticastSocket(5000);
			// join the network
			socket.joinGroup(InetAddress.getByName("224.0.0.5"));
			
			byte[] incoming = new byte[10000];
			System.out.println("Listening to incoming connections");
			
			// start receiving packets
			while (true) {
				DatagramPacket reqPacket = new DatagramPacket(incoming, incoming.length);
				socket.receive(reqPacket);
				
				// cast the object to the packet schema
				try {
					UdpPacket packet = (UdpPacket) deserialize(reqPacket.getData());
					
					System.out.println(packet.sequence + "th packet incoming");
					
					// add packet to queue map
					packetMap.put(packet.sequence, packet);
				} catch (ClassNotFoundException e) {
					e.printStackTrace();
				}
				
				if (socket.isClosed())
					break;
			}
			
			// wrap up
			socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	static Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
		try (ByteArrayInputStream b = new ByteArrayInputStream(bytes)) {
			try (ObjectInputStream o = new ObjectInputStream(b)) {
				return o.readObject();
			}
		}
	}
}
