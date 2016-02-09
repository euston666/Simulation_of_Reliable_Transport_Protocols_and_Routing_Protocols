
import java.util.*;
import java.io.*;

public class StudentNetworkSimulator extends NetworkSimulator {
    /*
     * Predefined Constants (static member variables):
     *
     *   int MAXDATASIZE : the maximum size of the Message data and
     *                     Packet payload
     *
     *   int A           : a predefined integer that represents entity A
     *   int B           : a predefined integer that represents entity B
     *
     * Predefined Member Methods:
     *
     *  void stopTimer(int entity):
     *       Stops the timer running at "entity" [A or B]
     *  void startTimer(int entity, double increment):
     *       Starts a timer running at "entity" [A or B], which will expire in
     *       "increment" time units, causing the interrupt handler to be
     *       called.  You should only call this with A.
     *  void toLayer3(int callingEntity, Packet p)
     *       Puts the packet "p" into the network from "callingEntity" [A or B]
     *  void toLayer5(String dataSent)
     *       Passes "dataSent" up to layer 5
     *  double getTime()
     *       Returns the current time in the simulator.  Might be useful for
     *       debugging.
     *  int getTraceLevel()
     *       Returns TraceLevel
     *  void //printEventList()
     *       Prints the current event list to stdout.  Might be useful for
     *       debugging, but probably not.
     *
     *
     *  Predefined Classes:
     *
     *  Message: Used to encapsulate a message coming from layer 5
     *    Constructor:
     *      Message(String inputData):
     *          creates a new Message containing "inputData"
     *    Methods:
     *      boolean setData(String inputData):
     *          sets an existing Message's data to "inputData"
     *          returns true on success, false otherwise
     *      String getData():
     *          returns the data contained in the message
     *  Packet: Used to encapsulate a packet
     *    Constructors:
     *      Packet (Packet p):
     *          creates a new Packet that is a copy of "p"
     *      Packet (int seq, int ack, int check, String newPayload)
     *          creates a new Packet with a sequence field of "seq", an
     *          ack field of "ack", a checksum field of "check", and a
     *          payload of "newPayload"
     *      Packet (int seq, int ack, int check)
     *          chreate a new Packet with a sequence field of "seq", an
     *          ack field of "ack", a checksum field of "check", and
     *          an empty payload
     *    Methods:
     *      boolean setSeqnum(int n)
     *          sets the Packet's sequence field to "n"
     *          returns true on success, false otherwise
     *      boolean setAcknum(int n)
     *          sets the Packet's ack field to "n"
     *          returns true on success, false otherwise
     *      boolean setChecksum(int n)
     *          sets the Packet's checksum to "n"
     *          returns true on success, false otherwise
     *      boolean setPayload(String newPayload)
     *          sets the Packet's payload to "newPayload"
     *          returns true on success, false otherwise
     *      int getSeqnum()
     *          returns the contents of the Packet's sequence field
     *      int getAcknum()
     *          returns the contents of the Packet's ack field
     *      int getChecksum()
     *          returns the checksum of the Packet
     *      int getPayload()
     *          returns the Packet's payload
     *
     */

    /*   Please use the following variables in your routines.
     *   int WindowSize  : the window size
     *   double RxmtInterval   : the retransmission timeout
     *   int LimitSeqNo  : when sequence number reaches this value, it wraps around
     */

	public static final int FirstSeqNo = 0;
	private int WindowSize;
	private double RxmtInterval;
	private int LimitSeqNo;

	// Add any necessary class variables here. Remember, you cannot use
	// these variables to send messages error free! They can only hold
	// state information for A or B.
	// Also add any necessary methods (e.g. checksum of a String)

	private int seqNumA; // sequence number on A side
	private int ackNumB; // ACK number on B side
	private int sndBase; // base of sender window
	private int rcvBase; // base of receiver window

	private int nTransmit = 0; // number of original data packets transmitted
	private int nRetransmit = 0; // number of retransmissions
	private int nACK = 0; // number of ack packets
	private int nCorrupt = 0; // number of corrupted packets received
	private int timesLogBase = 0; // base of times log

	private List<Message> msgList = new ArrayList<Message>(); // message buffer
	private List<Packet> pktOutList = new ArrayList<Packet>(); // sender window
	private List<Packet> pktInList = new ArrayList<Packet>(); // receiver window
	private List<double[]> timerLog = new ArrayList<double[]>(); // timer log (maintain timers on A side)
	private List<double[]> times = new ArrayList<double[]>(); // times log (for calculating the average communication
																// time)

	// This is the constructor. Don't touch!
	public StudentNetworkSimulator(int numMessages, double loss, double corrupt, double avgDelay, int trace, int seed,
			int winsize, double delay) {
		super(numMessages, loss, corrupt, avgDelay, trace, seed);
		WindowSize = winsize;
		LimitSeqNo = winsize + 1;
		RxmtInterval = delay;
	}

	// This routine will be called whenever the upper layer at the sender [A]
	// has a message to send. It is the job of your protocol to insure that
	// the data in such a message is delivered in-order, and correctly, to
	// the receiving upper layer.
	protected void aOutput(Message message) {

		msgList.add(message); // msg waits to send
		System.out.println("[Sender] message:" + message.getData() + " is buffered");

		int limit = ((sndBase + WindowSize) % LimitSeqNo) < sndBase ? LimitSeqNo : sndBase + WindowSize;

		if ((limit == LimitSeqNo && seqNumA < sndBase && seqNumA < (sndBase + WindowSize) % LimitSeqNo)
				|| (seqNumA >= sndBase && seqNumA < limit)) { // the seqnum is in the sender window

			Message msg = msgList.remove(0); // get a message from message buffer
			System.out.println("[Sender] message:" + msg.getData() + " is encapsulated and sent");

			Packet p = new Packet(seqNumA, 0, 0, msg.getData()); // checksum=0
			pktOutList.add(p); // buffered the sent packet
			times.add(new double[] { getTime(), 0 }); // log the send time
			toLayer3(A, p); // possibly lost or corrupted

			if (timerLog.size() == 0) { // generate the first interrupt event for packets
				startTimer(A, RxmtInterval);
			}

			timerLog.add(new double[] { seqNumA, getTime() + RxmtInterval }); // add a timer log

			seqNumA = (seqNumA + 1) % LimitSeqNo;

			printSenderInfo();
			//printEventList();

		} else { // the seqnum is not in the sender window, message is buffered for later sending
			System.out.println("message:" + message.getData() + " buffered and waits to send");

			printSenderInfo();
			//printEventList();

			return;
		}
	}

	// This routine will be called whenever a packet sent from the B-side
	// (i.e. as a result of a toLayer3() being done by a B-side procedure)
	// arrives at the A-side. "packet" is the (possibly corrupted) packet
	// sent from the B-side.
	protected void aInput(Packet packet) {

		if (!isCorrect(packet)) {
			System.out.println("[Sender] receive and ignore a corrupted ACK: " + packet);
			nCorrupt++;

			printSenderInfo();
			//printEventList();

			return;
		} else {
			System.out.println("[Sender] receive an uncorrupted ACK: " + packet);
			Packet p = markPacketAcked(packet.getAcknum()); // mark the packet ACKed (ack=1) in the sender window

			if (p != null) {
				System.out.println("[Sender] packet is ACKed: " + p);
				int i = stopPacketTimer(p); // remove the corresponding timer log

				if (i != -1) {
					System.out.println("[Sender] timer is stopped");
				} else {
					System.out.println("[Sender] timer cannot be stopped");
				}

				// if the first log is removed, generate an interrupt event for the next packet
				if (i == 0 && timerLog.size() != 0) {
					startTimer(A, timerLog.get(0)[1] - getTime());
				}

				printSenderInfo();
				//printEventList();

			} else {
				System.out.println("[Sender] this is a reACK, ignore it");

				printSenderInfo();
				//printEventList();

			}

			// if seqnum=base, advance packets to unacknowledged packet with the smallest seqnum
			if (p != null && p.getSeqnum() == sndBase) {
				int n = moveWindow();
				sndBase = (sndBase + n) % LimitSeqNo;
				timesLogBase += n; // forward the timesLogBase
				System.out.println("[Sender] sndBase moves forward, sndBase=" + sndBase);

				printSenderInfo();
				//printEventList();
			}

		}

	}

	// This routine will be called when A's timer expires (thus generating a
	// timer interrupt). You'll probably want to use this routine to control
	// the retransmission of packets. See startTimer() and stopTimer(), above,
	// for how the timer is started and stopped.
	protected void aTimerInterrupt() {
		System.out.println("[Sender] timer interrupt");

		Packet p = findTimeoutPacket(getTime()); // get the timeout packet
		if (p != null) { // restart timer
			System.out.println("[Sender] restart timer of packet: " + p);

			stopPacketTimer(p); // remove the old timer log
			timerLog.add(new double[] { p.getSeqnum(), getTime() + RxmtInterval }); // add the new timer log

			startTimer(A, timerLog.get(0)[1] - getTime()); // generate an interrupt event for next packet

			// resend the timeout packet
			System.out.println("[Sender] resend packet: " + p);
			toLayer3(A, p);
			nRetransmit++;

			printSenderInfo();
			//printEventList();

		} else { // packet is acked, and timer log has been removed, but there is still an interrupt event
			System.out.println("[Sender] timer has been stopped, ignore the interrupt");

			if (timerLog.size() != 0) {
				startTimer(A, timerLog.get(0)[1] - getTime()); // generate an interrupt event for next packet
			}

			printSenderInfo();
			//printEventList();

		}
	}

	// This routine will be called once, before any of your other A-side
	// routines are called. It can be used to do any required
	// initialization (e.g. of member variables you add to control the state
	// of entity A).
	protected void aInit() {
		sndBase = FirstSeqNo;
		seqNumA = FirstSeqNo;
		LimitSeqNo = 2 * WindowSize;
	}

	// This routine will be called whenever a packet sent from the B-side
	// (i.e. as a result of a toLayer3() being done by an A-side procedure)
	// arrives at the B-side. "packet" is the (possibly corrupted) packet
	// sent from the A-side.
	protected void bInput(Packet packet) {

		if (!isCorrect(packet)) {
			System.out.println("[Receiver] receive and discard a corrupted packet: " + packet);
			nCorrupt++;

			return; // ignore the corrupted packet
		} else { // uncorrupted packet
			int seq1 = packet.getSeqnum();

			// reACK a duplicate packet
			if (isBuffered(packet)) { // the packet is previously ACKed, this is a duplicate

				Packet p = new Packet(0, packet.getSeqnum(), 0);
				System.out.println("[Receiver] receive a duplicate packet, reACK: " + p);
				toLayer3(B, p);
				nACK++;

				return;
			}

			int limit1 = ((rcvBase + WindowSize) % LimitSeqNo) < rcvBase ? LimitSeqNo : rcvBase + WindowSize;

			// packets with seqnum in [base, base+WindowSize-1] is correctly received packet
			// it's buffered for delivery
			if ((limit1 == LimitSeqNo && seq1 < rcvBase && seq1 < (rcvBase + WindowSize) % LimitSeqNo)
					|| (seq1 >= rcvBase && seq1 < limit1)) {

				pktInList.add(packet); // add to the receiver window

				// ACK the correctly received packet
				Packet p1 = new Packet(0, packet.getSeqnum(), 0);
				System.out.println("[Receiver] ACK: " + p1);
				toLayer3(B, p1);
				nACK++;

				// if seqnum=base, deliver consecutive and ACKed packets
				if (packet.getSeqnum() == rcvBase) {
					int n = deliverPackets();
					nTransmit += n;
					rcvBase = (rcvBase + n) % LimitSeqNo;
					System.out.println("[Receiver] receiver base moves forward, rcvBase=" + rcvBase);
				}

				printReceiverInfo();
				//printEventList();

				return;

			} else {
				int oldBase = rcvBase + (rcvBase >= WindowSize ? (-WindowSize) : WindowSize);
				int limit2 = ((oldBase + WindowSize) % LimitSeqNo) < oldBase ? LimitSeqNo : oldBase + WindowSize;

				// packets with seqnum in [base-WindowSize, base-1] is previously acknowledged
				// it's acknowledged again
				if ((limit2 == LimitSeqNo && seq1 < oldBase && seq1 < (oldBase + WindowSize) % LimitSeqNo)
						|| (seq1 >= oldBase && seq1 < limit2)) {

					// reACK an old packet
					Packet p2 = new Packet(0, packet.getSeqnum(), 0);
					System.out.println("[Receiver] receive an old packet, reACK: " + p2);
					toLayer3(B, p2);
					nACK++;

					printReceiverInfo();
					//printEventList();

					return;
				}

			}
			// ignore the packet
		}
		// printInList();
	}

	// This routine will be called once, before any of your other B-side
	// routines are called. It can be used to do any required
	// initialization (e.g. of member variables you add to control the state
	// of entity B).
	protected void bInit() {

		ackNumB = FirstSeqNo;
		rcvBase = ackNumB;
	}

	// Use to print final statistics
	protected void Simulation_done() {
		System.out.println("number of packets transmitted: " + nTransmit);
		System.out.println("number of retransmission: " + nRetransmit);
		System.out.println("number of ack packets: " + nACK);
		System.out.println("number of corrupted packets received: " + nCorrupt);
		System.out.println("average time to communicate a packet: " + getAvgTime());
	}

	// check whether the packet is corrupted
	protected boolean isCorrect(Packet p) {
		String payload = p.getPayload();
		int newChecksum = (payload.indexOf("?") == -1) ? 0 : 1; // if corrupted, newchecksum = 1

		if (p.getChecksum() != newChecksum || p.getSeqnum() == 999999 || p.getAcknum() == 999999)
			return false;
		else
			return true;
	}

	// get the timeout packet from timer log (with timeout value)
	protected Packet findTimeoutPacket(double time) {
		// find the corresponding seqnum of "time"
		int seq = -1;
		for (double[] t : timerLog) {
			if (t[1] == time) {
				seq = (int) t[0];
				break;
			}
		}

		// find the index of packet with seq that we just get, and return the packet
		for (Packet p : pktOutList) {
			if (p.getSeqnum() == seq) {
				return p;
			}
		}

		return null;
	}

	// mark the acked packet in the sender window
	protected Packet markPacketAcked(int seq) {
		int index = -1;
		for (Packet p : pktOutList) {
			if (p.getSeqnum() == seq) {
				p.setAcknum(1);
				index = pktOutList.indexOf(p);
				if (times.get(timesLogBase + index)[1] == 0) {
					times.get(timesLogBase + index)[1] = getTime(); // log the ack time
					// printTimesLog();
				}

				return p;
			}
		}

		return null;
	}

	// the window base is moved forward to the unacknowledged packet with the smallest sequence number
	// return the removed amount of packets
	protected int moveWindow() {
		int n = 0;
		// number of consecutive packets that are acknowledged
		for (Packet p : pktOutList) {
			if (p.getAcknum() == 1) {
				n++;
			} else
				break;
		}
		// remove the acknowledged packets
		for (int i = 0; i < n; i++) {
			pktOutList.remove(0);
		}
		return n;
	}

	// remove the timer log of the packet, and return the index of the log
	protected int stopPacketTimer(Packet p) {
		int index = -1;
		for (double[] t : timerLog) {
			if ((int) t[0] == p.getSeqnum()) {
				index = timerLog.indexOf(t);
				timerLog.remove(t);
				return index;
			}
		}
		return index;
	}

	// deliver consecutive packets (start with receiver base) to Layer5 on B side
	protected int deliverPackets() {
		int n = 0;
		int seq = rcvBase;
		Packet p = getBufferedPacket(seq);
		while (p != null) {
			System.out.println("deliver packet: " + p);
			toLayer5(p.getPayload());
			pktInList.remove(p);
			n++;
			seq = (seq + 1) % LimitSeqNo;
			p = getBufferedPacket(seq);
		}
		return n;
	}

	// get buffered packet from receiver window
	protected Packet getBufferedPacket(int seq) {
		for (Packet p1 : pktInList) {
			if (p1.getSeqnum() == seq) {
				return p1;
			}
		}
		return null;
	}

	// check if the packet is buffered in the receiver window
	protected boolean isBuffered(Packet packet) {
		for (Packet p : pktInList) {
			if (packet.getSeqnum() == p.getSeqnum()) {
				return true;
			}
		}
		return false;
	}

	// calculate the average time to communicate a packet
	protected double getAvgTime() {
		double sum = 0;
		int n = 0;
		for (double[] t : times) {
			if (t[1] == 0)
				break;
			sum += (t[1] - t[0]);
			n++;
		}
		return sum / n;
	}

	// print the sender window
	protected void printOutList() {
		System.out.println("Sender Window:");
		for (Packet p : pktOutList) {
			System.out.println(p);
		}
		System.out.println();
	}

	// print the receiver window
	protected void printInList() {
		System.out.println("Receiver Window:");
		for (Packet p : pktInList) {
			System.out.println(p);
		}
		System.out.println();
	}

	// print the message buffer
	protected void printMsgList() {
		System.out.println("Buffered Messages:");
		for (Message m : msgList) {
			System.out.println(m.getData());
		}
		System.out.println();
	}

	// print the message buffer and sender window
	protected void printSenderInfo() {
		// printMsgList();
		printOutList();
	}

	// print the receiver window
	protected void printReceiverInfo() {
		printInList();
	}

	// print the times log (for debugging)
	protected void printTimesLog() {
		System.out.println("times log:");
		for (double[] t : times) {
			System.out.println("send time: " + t[0] + "  ack time: " + t[1]);
		}
		System.out.println();
	}

	// print timer logs (for debugging)
	protected void printTimerLog() {
		System.out.println("timerLog:");
		for (double[] t : timerLog) {
			System.out.println("seq=" + (int) t[0] + " timer=" + t[1]);
		}
		System.out.println();

	}

}
