import implementation.CampusOperations;
import implementation.DataHolder;
import implementation.UdpThread;
import schema.Campus;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.logging.FileHandler;
import java.util.logging.Logger;

public class CampusServer {
    public static void main(String[] args) {
        DataHolder dataHolder = new DataHolder();
        String servers;
        Logger logs;
        CampusOperations campusOps;

        if (args.length != 2) {
            System.out.println("Usage: java CampusServer <server-directory> <index-to-run>\nWhere, <server-directory> is the list of the servers. and <index-to-run> is the index of the server this file would run");
            return;
        }

        int choice = Integer.parseInt(args[1]);

        try {
            BufferedReader reader = new BufferedReader(new FileReader(args[0]));
            servers = reader.readLine();
        } catch (IOException ioe) {
            System.out.println(ioe.getMessage());
            return;
        }

        // add the network of campus servers to the memory
        // add the network of campus servers to the memory
        String[] campusList = servers.split(";");
        for (String item : campusList) {
            String[] campusDetails = item.split(",");
            Campus c = new Campus(Integer.parseInt(campusDetails[2].trim()), campusDetails[1].trim().toUpperCase(), campusDetails[0].trim());
            dataHolder.campuses.add(c);
        }
        dataHolder.campus = dataHolder.campuses.get(choice);


        // set up the logging mechanism
        logs = Logger.getLogger(dataHolder.campus.name + " Server");
        try {
            FileHandler fileHandler = new FileHandler(dataHolder.campus.name.replace(" ", "-").toLowerCase() + ".log", true);
            logs.addHandler(fileHandler);
        } catch(IOException ioe) {
            logs.warning("Failed to create handler for log file.\n Message: " + ioe.getMessage());
        }

        // initialize the implementation class
        campusOps = new CampusOperations(logs, dataHolder);

        // start the udp server
        try {
            DatagramSocket udpSocket = new DatagramSocket(dataHolder.campus.getUdpPort());
            byte[] incoming = new byte[10000];
            logs.info("The UDP server for " + dataHolder.campus.name + " is up and running on port " + dataHolder.campus.getUdpPort());

            // fetch the data after a second (give it a second to load)
            campusOps.importData();

            while (true) {
                DatagramPacket packet = new DatagramPacket(incoming, incoming.length);
                try {
                    udpSocket.receive(packet);
                    UdpThread thread = new UdpThread(udpSocket, packet, campusOps, logs);
                    thread.start();
                } catch (IOException ioe) {
                    logs.warning("Exception thrown while receiving packet.\nMessage: " + ioe.getMessage());
                }
                if (udpSocket.isClosed())
                    break;
            }
        } catch (SocketException e) {
            logs.warning("Exception thrown while server was running/trying to start.\nMessage: " + e.getMessage());
        }
    }
}
