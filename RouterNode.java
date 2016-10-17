import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.swing.*;

public class RouterNode {
  private int myID;
  private GuiTextArea myGUI;
  private RouterSimulator sim;
  private int[] origCosts = new int[RouterSimulator.NUM_NODES];
  // Added variables
  private int[] actCosts = new int[RouterSimulator.NUM_NODES];
  private int[] neighbours;   // IDs of my direct neighbours
  private int numNeighbours;  // Number of direct neighbours
  private HashMap<Integer, int[]> routingTable;
  private boolean poisonReverse = true;  // Modify the value here
  private int[] route;

  //--------------------------------------------------
  public RouterNode(int ID, RouterSimulator sim, int[] origCosts) {
    myID = ID;
    this.sim = sim;
    myGUI = new GuiTextArea("  Output window for Router #"+ ID + "  ");

    System.arraycopy(origCosts, 0, this.origCosts, 0, RouterSimulator.NUM_NODES);
    System.arraycopy(origCosts, 0, this.actCosts, 0, RouterSimulator.NUM_NODES);

    // ADDED CODE:

    // Directly neighbours for this nodes
    neighbours = new int[RouterSimulator.NUM_NODES];
    numNeighbours = 0;

    // Initialise the vector for the poison reverse
    route = new int[RouterSimulator.NUM_NODES];

    // Initialise routing table with our distances
    routingTable = new HashMap<Integer, int[]>();

    int[] unknown = new int[RouterSimulator.NUM_NODES];
    Arrays.fill(unknown, RouterSimulator.INFINITY);

    for (int i = 0; i < RouterSimulator.NUM_NODES; i++) {
      if (i == ID) {
    	  routingTable.put(ID, this.origCosts);
      }
      else {
    	  routingTable.put(i, unknown);
      }

      // And also initialise the route
      if (origCosts[i] == RouterSimulator.INFINITY)
    	  route[i] = RouterSimulator.INFINITY;
      else {
    	  route[i] = i;
        if (i != ID) {
          neighbours[numNeighbours] = i;
          numNeighbours++;
        }
      }
    }

    alertNeighbours();  // And send the info
  }

  //--------------------------------------------------
  public void recvUpdate(RouterPacket pkt) {
	  int from = pkt.sourceid;
	  int[] hisDistance = new int[RouterSimulator.NUM_NODES];
	  System.arraycopy(pkt.mincost, 0, hisDistance, 0, RouterSimulator.NUM_NODES);

	  this.routingTable.put(from, hisDistance);

	  processUpdate();
  }

//--------------------------------------------------
  private void sendUpdate(RouterPacket pkt) {
    sim.toLayer2(pkt);

  }


  //--------------------------------------------------
  public void printDistanceTable() {
	  myGUI.println("Current table for " + myID +
			"  at time " + sim.getClocktime());
	  myGUI.println("");

	  myGUI.println("Distancetable:");
    // Routing table headers
	  String line = "    dst  |";
	  for(int i = 0; i < RouterSimulator.NUM_NODES; ++i) {
      line += ("    " + i);
    }
	  myGUI.println(line);

    String separator = "";
    for (int i = 0; i < line.length(); i++) {
      separator += "-";
    }
	  myGUI.println(separator);
    // Table content
	  for (int j = 0; j < numNeighbours; j++) {
      int nbr = neighbours[j];
    	int[] distanceVector = routingTable.get(nbr);
    	line = " nbr  " + nbr + " |";
		  for (int i = 0; i < RouterSimulator.NUM_NODES; ++i) {
   	 		line += "    " + distanceVector[i];
    	}
		  myGUI.println(line);
    }
	  myGUI.println("");


	  myGUI.println("Our distance vector and routes:");
    line = "    dst  |";
	  for(int i = 0; i < RouterSimulator.NUM_NODES; ++i) {
      line += ("    " + i);
    }
	  myGUI.println(line);
	  myGUI.println(separator);

	  line = " cost   |";
	  for(int i = 0; i < RouterSimulator.NUM_NODES; ++i) {
    	line += "    " + actCosts[i];
    }
	  myGUI.println(line);
	  line = " route  |";
	  for(int i = 0; i < RouterSimulator.NUM_NODES; ++i) {
    	if (route[i] == RouterSimulator.INFINITY) {
        line += "    -";
      }
      else {
        line += "    " + route[i];
      }
    }
	  myGUI.println(line);
    myGUI.println("");
  }

  //--------------------------------------------------
  public void updateLinkCost(int dest, int newcost) {
	  this.actCosts[dest] = newcost;
	  this.origCosts[dest] = newcost;
    this.route[dest] = dest;  // Reset the route

	  processUpdate();
  }

  private void processUpdate() {
	  boolean changed = false;
	  // Actualise the routing table
	  for (Integer it : routingTable.keySet()) {
	    if (it == myID){
        this.actCosts[it] = 0;
        this.route[it] = myID;
		    continue;
      }

	    int act = RouterSimulator.INFINITY;
	    int shortcut = it;  // The ID of the neighbour I route through

	    // We check the minimum cost to each neighbour
	    for (Map.Entry<Integer, int[]> p : routingTable.entrySet()) {
		    int nbr = p.getKey();
		    int cost = p.getValue()[it] + this.origCosts[nbr];

		    if (cost < act) {
			    act = cost;
			    shortcut = nbr;
		    }
	    }
      if (origCosts[it] <= act) {
        act = origCosts[it];
        shortcut = it;
      }

	    // Now we have the shortest path for that neighbour
	    if (act != this.actCosts[it]) {
		    this.actCosts[it] = act;
		    this.route[it] = shortcut;
		    changed = true;
	    }
	  }

	  if (changed) {
	    alertNeighbours();
	  }
  }

  private void alertNeighbours() {
	  // Inform all the neighbours about the changes
	  for (Integer it : routingTable.keySet()) {
		  RouterPacket pkt;
		  if (!poisonReverse) {
		    pkt = new RouterPacket(myID, it, actCosts);
		  }
		  else {
		    int[] whiteLie = new int[RouterSimulator.NUM_NODES];
		    System.arraycopy(this.actCosts, 0, whiteLie, 0, RouterSimulator.NUM_NODES);
		    for (int i = 0; i < RouterSimulator.NUM_NODES; i++) {
			    if (this.route[i] == it) {
			      // If I go through it in order to get to i...
			      whiteLie[i] = RouterSimulator.INFINITY;
			    }
		    }
		    pkt = new RouterPacket(myID, it, whiteLie);
		  }
		  sendUpdate(pkt);
	  }
  }
}
