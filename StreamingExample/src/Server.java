/* ------------------
 Server
 usage: java Server [RTSP listening port]
 ---------------------- */

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.NoSuchElementException;
import java.util.SimpleTimeZone;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.Timer;

public class Server extends JFrame implements ActionListener {

    //RTP variables:
    //----------------
    DatagramSocket rtpSocket; //socket to be used to send and receive UDP packets
    DatagramPacket sendPacket; //UDP packet containing the video frames
    InetAddress clientIPAddr; //Client IP address
    int rtpDestPort = 0; //destination port for RTP packets  (given by the RTSP Client)
    //GUI:
    //----------------
    JLabel label;
    //Video variables:
    //----------------
    int imagenb = 0; //image nb of the image currently transmitted
    VideoStream video; //VideoStream object used to access video frames
    static int MJPEG_TYPE = 26; //RTP payload type for MJPEG video
    static int FRAME_PERIOD = 100; //Frame period of the video to stream, in ms
    static int VIDEO_LENGTH = 500; //length of the video in frames
    Timer timer; //timer used to send the images at the video frame rate
    byte[] buffer; //buffer used to store the images to send to the client
    //RTSP variables
    //----------------
    //rtsp states
    final static int INIT = 0;
    final static int READY = 1;
    final static int PLAYING = 2;
    //rtsp message types
    final static int SETUP = 3;
    final static int PLAY = 4;
    final static int PAUSE = 5;
    final static int TEARDOWN = 6;
    final static int OPTIONS = 7;
    final static int DESCRIBE = 8;
    final static int UNKNOWN = 9;
    static int state; //RTSP Server state == INIT or READY or PLAY
    Socket rtspSocket; //socket used to send/receive RTSP messages
    //input and output stream filters
    static BufferedReader rtspBufferedReader;
    static BufferedWriter rtspBufferedWriter;
    static String videoFileName; //video file requested from the client
    static int RTSP_ID = 123456; //ID of the RTSP session
    int rtspSeqNb = 0; //Sequence number of RTSP messages within the session
    final static String CRLF = "\r\n";

    //--------------------------------
    //Constructor
    //--------------------------------
    public Server() {

        //init Frame
        super("Server");

        //init Timer
        timer = new Timer(FRAME_PERIOD, this);
        timer.setInitialDelay(0);
        timer.setCoalesce(true);

        //allocate memory for the sending buffer
        buffer = new byte[15000];

        //Handler to close the main window
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                //stop the timer and exit
                timer.stop();
                System.exit(0);
            }
        });

        //GUI:
        label = new JLabel("Send frame #        ", JLabel.CENTER);
        getContentPane().add(label, BorderLayout.CENTER);
    }

    //------------------------------------
    //main
    //------------------------------------
    public static void main(String argv[]) throws Exception {
        if (argv.length != 1) {
            System.out.println("Usage: java Server RTSP_listening_port");
            System.exit(0);
        }
        //create a Server object
        Server theServer = new Server();

        //show GUI:
        theServer.pack();
        theServer.setVisible(true);

        //get RTSP socket port from the command line
        int rtspPort = Integer.parseInt(argv[0]);

        //Initiate TCP connection with the client for the RTSP session
        ServerSocket listenSocket = new ServerSocket(rtspPort);
        theServer.rtspSocket = listenSocket.accept();
        listenSocket.close();

        //Get Client IP address
        theServer.clientIPAddr = theServer.rtspSocket.getInetAddress();

        //Initiate RTSPstate
        state = INIT;

        //Set input and output stream filters:
        rtspBufferedReader = new BufferedReader(new InputStreamReader(theServer.rtspSocket.getInputStream()));
        rtspBufferedWriter = new BufferedWriter(new OutputStreamWriter(theServer.rtspSocket.getOutputStream()));

        //Wait for the SETUP message from the client
        int requestType;
        boolean done = false;
        
//        while (!done) {
//
//            requestType = theServer.parseRTSPRequest(); //blocking
//
//
//            if (requestType == OPTIONS) {
//                done = true;
//
//                //Send response
//                theServer.sendOptions();
//
//            }
//        }
//        done = false;
//        while (!done) {
//
//            requestType = theServer.parseRTSPRequest(); //blocking
//
//
//            if (requestType == SETUP) {
//                done = true;
//
//                //update RTSP state
//                state = READY;
//                System.out.println("New RTSP state: READY");
//
//                //Send response
//                theServer.sendRTSPResponse();
//
//                //init the VideoStream object:
//                theServer.video = new VideoStream(videoFileName);
//
//                //init RTP socket
//                theServer.rtpSocket = new DatagramSocket();
//            }
//        }

        //loop to handle RTSP requests
        while (true) {
            //parse the request
            requestType = theServer.parseRTSPRequest(); //blocking
            if (requestType == SETUP && !done) {
                done = true;

                //update RTSP state
                state = READY;
                System.out.println("New RTSP state: READY");
                
                //init RTP socket BEFORE the response so we know what port to use. this was not good example code.
                theServer.rtpSocket = new DatagramSocket();
                
                //Send response
                theServer.sendSETUPResponse();

                //init the VideoStream object:
                theServer.video = new VideoStream(videoFileName);

                
            }
            else if ((requestType == PLAY) && (state == READY)) {
                //send back response
                theServer.sendRTSPResponse();
                //start timer
                theServer.timer.start();
                //update state
                state = PLAYING;
                System.out.println("New RTSP state: PLAYING");
            } else if ((requestType == PAUSE) && (state == PLAYING)) {
                //send back response
                theServer.sendRTSPResponse();
                //stop timer
                theServer.timer.stop();
                //update state
                state = READY;
                System.out.println("New RTSP state: READY");
            } else if (requestType == TEARDOWN) {
                //send back response
                theServer.sendRTSPResponse();
                //stop timer
                theServer.timer.stop();
                //close sockets
                theServer.rtspSocket.close();
                theServer.rtpSocket.close();

                System.exit(0);
            } else if (requestType == OPTIONS) {
                //Send response
                theServer.sendOptions();

            } else if (requestType == DESCRIBE){
            	theServer.sendDescription();
            }
        }
    }

    //------------------------
    //Handler for timer
    //------------------------
    public void actionPerformed(ActionEvent e) {

        //if the current image nb is less than the length of the video
        if (imagenb < VIDEO_LENGTH) {
            //update current imagenb
            imagenb++;

            try {
                //get next frame to send from the video, as well as its size
                int imageLength = video.getNextFrame(buffer);

                //Builds an RTPPacket object containing the frame
                RTPPacket rtpPacket = new RTPPacket(MJPEG_TYPE, imagenb, imagenb * FRAME_PERIOD, buffer, imageLength);

                //get to total length of the full rtp packet to send
                int packetLength = rtpPacket.getLength();

                //retrieve the packet bitstream and store it in an array of bytes
                byte[] packetBits = new byte[packetLength];
                rtpPacket.getPacket(packetBits);

                //send the packet as a DatagramPacket over the UDP socket 
                sendPacket = new DatagramPacket(packetBits, packetLength, clientIPAddr, rtpDestPort);
                rtpSocket.send(sendPacket);

                System.out.println("Send frame #" + imagenb);
                //print the header bitstream
                rtpPacket.printHeader();

                //update GUI
                label.setText("Send frame #" + imagenb);
            } catch (Exception ex) {
                System.out.println("Exception caught: " + ex);
                System.exit(0);
            }
        } else {
            //if we have reached the end of the video file, stop the timer
            timer.stop();
        }
    }

    //------------------------------------
    //Parse RTSP Request
    //------------------------------------
    private int parseRTSPRequest() {
        int requestType = -1;
        try {
            //parse request line and extract the requestType:
            String RequestLine = rtspBufferedReader.readLine();
            if(RequestLine == null){
            	return -1;
            }
            if(RequestLine.isEmpty()){
            	System.out.println("is empty");
            	return -1;
            }
            System.out.println("RTSP Server - Received from Client:");
            System.out.println(RequestLine);

            StringTokenizer tokens = new StringTokenizer(RequestLine);
            String requestTypeString = tokens.nextToken();

            //convert to requestType structure:
            if (requestTypeString.compareTo("SETUP") == 0) {
                requestType = SETUP;
            } else if (requestTypeString.compareTo("PLAY") == 0) {
                requestType = PLAY;
            } else if (requestTypeString.compareTo("PAUSE") == 0) {
                requestType = PAUSE;
            } else if (requestTypeString.compareTo("TEARDOWN") == 0) {
                requestType = TEARDOWN;
            } else if (requestTypeString.compareTo("OPTIONS") == 0) {
                requestType = OPTIONS;
            } else if (requestTypeString.compareTo("DESCRIBE") == 0) {
                requestType = DESCRIBE;
            }
            
            if (requestType == SETUP) {
                //extract videoFileName from RequestLine
            	
            	//!!NOT WORKING BUT SHOULD BE IMPLEMENTED!!
               // videoFileName = tokens.nextToken();
            	videoFileName = "media/movie.Mjpeg";
            }

            //parse the seqNumLine and extract CSeq field
            String seqNumLine = rtspBufferedReader.readLine();
            System.out.println(seqNumLine);
            tokens = new StringTokenizer(seqNumLine);
            tokens.nextToken();
            rtspSeqNb = Integer.parseInt(tokens.nextToken());
            //get lastLine
            String lastLine = rtspBufferedReader.readLine();
            System.out.println(lastLine);

            if (requestType == SETUP) {
            	String portInfoLine = rtspBufferedReader.readLine();
                System.out.println(portInfoLine);
            	Pattern portPattern = Pattern.compile("client_port=(\\d+)");
            	Matcher portMatcher = portPattern.matcher(portInfoLine);
            	if(portMatcher.find()){
            		String port = portMatcher.group(1);
            		rtpDestPort = Integer.parseInt(port);
            	}else{
            		System.out.println("No dest port found");
            	}
            	
            	System.out.println("found dest port: " + rtpDestPort);
                //extract rtpDestPort from lastLine
//                tokens = new StringTokenizer(lastLine);
//                for (int i = 0; i < 3; i++) {
//                    tokens.nextToken(); //skip unused stuff
//
//                }
//                rtpDestPort = Integer.parseInt(tokens.nextToken());
            } else if(requestType == DESCRIBE){
            	//Describe has one more line than usual
            	String line = rtspBufferedReader.readLine();
                System.out.println(line);
            }	else if(requestType == PLAY){
            	//Play has two more lines
            	String sess = rtspBufferedReader.readLine();
            	System.out.println(sess);
            	String line = rtspBufferedReader.readLine();
                System.out.println(line);
            }
            //else lastLine will be the SessionId line ... do not check for now.
        } catch (IOException ex) {
            System.out.println("Exception caught: " + ex);
            ex.printStackTrace();
            System.exit(0);
        } catch (NumberFormatException ex) {
            System.out.println("Exception caught: " + ex);
            ex.printStackTrace();
            System.exit(0);
        } catch (NoSuchElementException ex) {
            return -1;
        }
        return (requestType);
    }
    
//    private static void sysoRTSPRequest() {
//    	try {
//    		while(true){
//			String line = rtspBufferedReader.readLine();
//			System.out.println(line);
//    		}
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//    }

    //------------------------------------
    //Send RTSP Response
    //------------------------------------
    private void sendRTSPResponse() {
        try {
            rtspBufferedWriter.write("RTSP/1.0 200 OK" + CRLF);
            rtspBufferedWriter.write("CSeq: " + rtspSeqNb + CRLF);
            rtspBufferedWriter.write("Session: " + RTSP_ID + CRLF + CRLF);
            rtspBufferedWriter.flush();
            System.out.println("RTSP Server - Sent response to Client.");
        } catch (IOException ex) {
            System.out.println("Exception caught: " + ex);
            System.exit(0);
        }
    }
    
    private void sendSETUPResponse() {
    	String s = "Transport: RTP/AVP;unicast;client_port=" + rtpDestPort + ";server_port=" + rtpSocket.getLocalPort();
        try {
            rtspBufferedWriter.write("RTSP/1.0 200 OK" + CRLF);
            rtspBufferedWriter.write("CSeq: " + rtspSeqNb + CRLF);
            rtspBufferedWriter.write(s + CRLF);
            rtspBufferedWriter.write("Session: " + RTSP_ID + CRLF + CRLF);
            rtspBufferedWriter.flush();
            System.out.println("RTSP Server - Sent response to Client.");
        } catch (IOException ex) {
            System.out.println("Exception caught: " + ex);
            System.exit(0);
        }
    }
    
    private void sendOptions() {
        try {
            rtspBufferedWriter.write("RTSP/1.0 200 OK" + CRLF);
            rtspBufferedWriter.write("CSeq: " + rtspSeqNb + CRLF);
            rtspBufferedWriter.write("Public: DESCRIBE, SETUP, TEARDOWN, PLAY, PAUSE" + CRLF + CRLF);
            rtspBufferedWriter.flush();
            System.out.println("RTSP Server - Sent options to Client.");
        } catch (IOException ex) {
            System.out.println("Exception caught: " + ex);
            System.exit(0);
        }
    }
    private void sendDescription() {
    	SimpleDateFormat sdf = new SimpleDateFormat();
    	sdf.setTimeZone(new SimpleTimeZone(0, "GMT"));
    	sdf.applyPattern("dd MMM yyyy HH:mm:ss z");
    	Date now = new Date();
    	String date = sdf.format(now);
    	System.out.println("sending description");
    	//version
    	String desc = "v=0" + CRLF;
    	//session identifier: <username> <sess-id> <sess-version> <nettype> <addrtype> <unicast-address>
    	//- because no username, made up sess-id and version. IN=internet
    	desc += "o=- 123456789 1 IN IP4 127.0.0.1" + CRLF;
    	//session name
    	desc += "s=projekt" + CRLF;
    	//startime, endtime. hopefully this should last forever
    	desc += "t=0 0" + CRLF;
    	//<media> <port> <proto> <fmt>
    	//if rtp then <fmt> must be set to payload type number: http://en.wikipedia.org/wiki/RTP_audio_video_profile
    	desc += "m=video 0 RTP/AVP 26" + CRLF;
    	String cLength = Integer.toString(desc.length());
        try {
            rtspBufferedWriter.write("RTSP/1.0 200 OK" + CRLF);
            rtspBufferedWriter.write("CSeq: " + rtspSeqNb + CRLF);
            rtspBufferedWriter.write("Content-Type: application/sdp" + CRLF);
            rtspBufferedWriter.write("Content-Base: rtsp://127.0.0.1:6666" + CRLF);
            rtspBufferedWriter.write("Date: " + date + CRLF);
            rtspBufferedWriter.write("Content-Length: " + cLength + CRLF + CRLF);
            rtspBufferedWriter.write(desc);
            rtspBufferedWriter.flush();
            System.out.println("RTSP Server - Sent options to Client.");
        } catch (IOException ex) {
            System.out.println("Exception caught: " + ex);
            System.exit(0);
        }
    }
}
