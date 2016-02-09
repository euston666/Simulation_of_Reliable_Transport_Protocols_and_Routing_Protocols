
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
	private int ackNumB; // acknowledge number on B side
	private int base; // base of the sender window

	private int nTransmit = 0; // number of original data packets transmitted
	private int nRetransmit = 0; // number of retransmissions
	private int nACK = 0; // number of ack packets
	private int nCorrupt = 0; // number of corrupted packets received
	private int nCommunicate = 0; // base of times log

	private List<Message> msgList = new ArrayList<Message>(); // message buffer
	private List<Packet> pktOutList = new ArrayList<Packet>(); // sender window
	private List<Packet> pktInList = new ArrayList<Packet>(); // receiver window
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

		msgList.add(message); // msg waits for send
		System.out.println("[Sender] message:" + message.getData() + " is buffered");

		int limit = ((base + WindowSize) % LimitSeqNo) < base ? LimitSeqNo : base + WindowSize;

		if ((limit == LimitSeqNo && seqNumA < base && seqNumA < (base + WindowSize) % LimitSeqNo)
				|| (seqNumA >= base && seqNumA < limit)) {

			Message msg = msgList.remove(0);
			System.out.println("[Sender] message:" + msg.getData() + " is encapsulated and sent");

			Packet p = new Packet(seqNumA, 0, 0, msg.getData()); // checksum=0
			pktOutList.add(p); // buffered the sent packet
			toLayer3(A, p); // possibly lost or corrupted

			times.add(new double[] { getTime(), 0 }); // log the send time

			if (base == seqNumA) {
				startTimer(A, RxmtInterval);
			}
			seqNumA = (seqNumA + 1) % LimitSeqNo;
		}

		printSenderInfo();
		//printEventList();
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
			base = (packet.getAcknum() + 1) % LimitSeqNo;

			int n = refreshOutList(); // move the window forward
			setACKTime(n);
			nCommunicate += n;

			if (base == seqNumA) { // there are no outstanding, unacknowledged packets
				stopTimer(A);
			} else { // restart timer
				stopTimer(A);
				startTimer(A, RxmtInterval);
			}

			printSenderInfo();
			//printEventList();
		}
	}

	// This routine will be called when A's timer expires (thus generating a
	// timer interrupt). You'll probably want to use this routine to control
	// the retransmission of packets. See startTimer() and stopTimer(), above,
	// for how the timer is started and stopped.
	protected void aTimerInterrupt() {
		System.out.println("[Sender] timer interrupt");
		startTimer(A, RxmtInterval);

		// resend all the unacknowledged packets
		for (Packet p : pktOutList) {
			System.out.println("[Sender] retransmit packet: " + p);
			toLayer3(A, p);
			nRetransmit++;
		}

		printSenderInfo();
		//printEventList();
	}

	// This routine will be called once, before any of your other A-side
	// routines are called. It can be used to do any required
	// initialization (e.g. of member variables you add to control the state
	// of entity A).
	protected void aInit() {
		base = FirstSeqNo;
		seqNumA = FirstSeqNo;
	}

	// This routine will be called whenever a packet sent from the B-side
	// (i.e. as a result of a toLayer3() being done by an A-side procedure)
	// arrives at the B-side. "packet" is the (possibly corrupted) packet
	// sent from the A-side.
	protected void bInput(Packet packet) {

		if (!isCorrect(packet)) {
			System.out.println("[Receiver] receive and discard a corrupted packet: " + packet);
			nCorrupt++;

			if (pktInList.size() == 0) {
				return; // the first packet doesn't arrive
			}

			// reACK a previously acknowledged packet
			Packet p1 = new Packet(0, pktInList.get(0).getSeqnum(), 0);
			System.out.println("[Receiver] reACK: " + p1);
			toLayer3(B, p1);

			nACK++;

			printReceiverInfo();
			//printEventList();

			return;
		} else {
			if (packet.getSeqnum() == ackNumB) { // the expected packet
				System.out.println("[Receiver] receive an expected packet: " + packet);
				if (pktInList.size() != 0) { // size=0: the first packet arrives
					pktInList.remove(0);
				}

				pktInList.add(packet); // buffer the uncorrupted packet
				System.out.println("[Receiver] packet payload " + packet.getPayload()
						+ " is delivered to Layer5 on B side");
				toLayer5(packet.getPayload()); // deliver payload to Layer5 on B side
				nTransmit++;

				// ACK the expected packet
				Packet p = new Packet(0, ackNumB, 0);
				System.out.println("[Receiver] ACK: " + p);
				toLayer3(B, p);

				nACK++;
				ackNumB = (packet.getSeqnum() + 1) % LimitSeqNo;

				printReceiverInfo();
				//printEventList();
			} else {
				System.out.println("[Receiver] receive an unexpected packet: " + packet);
				System.out.println("[Receiver] the unexpected packet is discarded");
				if (pktInList.size() == 0) {
					return; // the first packet doesn't arrive
				}

				// when the receiver receive an unexpected packet, reACK the previously acknowledged packet
				Packet p2 = new Packet(0, pktInList.get(0).getSeqnum(), 0);
				System.out.println("[Receiver] reACK: " + p2);
				toLayer3(B, p2);
				nACK++;

				printReceiverInfo();
				//printEventList();
			}
		}
	}

	// This routine will be called once, before any of your other B-side
	// routines are called. It can be used to do any required
	// initialization (e.g. of member variables you add to control the state
	// of entity B).
	protected void bInit() {
		ackNumB = FirstSeqNo;
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

	// delete the cumulatively acknowledged packets from pktOutList
	// return number of cumulatively acknowledged packets
	protected int refreshOutList() {
		int index = -1;
		int end = (base == 0 ? (base - 1 + LimitSeqNo) : (base - 1));
		for (Packet p : pktOutList) {
			if (p.getSeqnum() == end) {
				index = pktOutList.indexOf(p);
				break;
			}
		}

		for (int i = 0; i <= index; i++) {
			pktOutList.remove(0);
		}

		return index + 1;
	}

	// set the first ack time of a packet
	protected void setACKTime(int n) {
		for (int i = nCommunicate; i < nCommunicate + n; i++) {
			if (times.get(i)[1] == 0) {
				times.get(i)[1] = getTime();
			}
		}

		// printTimesLog();
	}

	// get the average time to comunicate a packet
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

	// print the times log
	protected void printTimesLog() {
		System.out.println("times log:");
		for (double[] t : times) {
			System.out.println("send time: " + t[0] + "  ack time: " + t[1]);
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

}
