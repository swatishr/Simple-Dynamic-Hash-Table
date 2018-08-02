package edu.buffalo.cse.cse486586.simpledht;

/**
 * Created by swati on 4/10/18.
 */

public class NodeMessage extends Message{

    int newPortNum;
    int predecessor;
    int successor;

    public NodeMessage(String msgType, String originNode, int newPortNum, int destNode) {
        super(msgType, originNode, destNode);
        this.newPortNum = newPortNum;
    }

    public NodeMessage(String msgType, int predecessor, int successor, int destNode) {
        this.msgType = msgType;
        this.destNode = destNode;
        this.predecessor = predecessor;
        this.successor = successor;
    }

    @Override
    public String toString() {
        return  msgType + "#" + originNode + "#" + destNode + "#" + newPortNum + "#" + predecessor + "#" + successor;
    }
}
