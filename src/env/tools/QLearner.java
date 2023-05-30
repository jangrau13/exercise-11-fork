package tools;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.*;
import java.util.logging.*;
import cartago.Artifact;
import cartago.OPERATION;
import cartago.OpFeedbackParam;

public class QLearner extends Artifact {

  /**
   *
   */
  private static final int ITERATIONS = 100;
  private static final String FILENAME = "with_greedy.ser";
  private Lab lab; // the lab environment that will be learnt
  private int stateCount; // the number of possible states in the lab environment
  private int actionCount; // the number of possible actions in the lab environment
  private HashMap<String, double[][]> qTables = new HashMap<>(); // a map for storing the qTables computed for
                                                                 // different goals

  private static final Logger LOGGER = Logger.getLogger(QLearner.class.getName());
  //remember to not take actions that reverses the action just previously taken
  List<Integer> lastActions = new ArrayList<>(2);

  public void init(String environmentURL) {

    // the URL of the W3C Thing Description of the lab Thing
    this.lab = new Lab(environmentURL);

    lastActions.add(0, 0);
    lastActions.add(1, 0);

    this.stateCount = this.lab.getStateCount();
    LOGGER.info("Initialized with a state space of n=" + stateCount);

    this.actionCount = this.lab.getActionCount();
    LOGGER.info("Initialized with an action space of m=" + actionCount);

    Integer currentState = this.lab.readCurrentState();
    Random random = new Random();
    for (int i = 0; i < ITERATIONS; i++) {
      List<Integer> possibleActions = this.lab.getApplicableActions(currentState);
      int randomAction = possibleActions.get(random.nextInt(possibleActions.size()));
      this.lab.performAction(randomAction);
    }

    currentState = this.lab.readCurrentState();
    System.out.println("current State: " + currentState);

  }

  /**
   * Computes a Q matrix for the state space and action space of the lab, and
   * against
   * a goal description. For example, the goal description can be of the form
   * [z1level, z2Level],
   * where z1Level is the desired value of the light level in Zone 1 of the lab,
   * and z2Level is the desired value of the light level in Zone 2 of the lab.
   * For exercise 11, the possible goal descriptions are:
   * [0,0], [0,1], [0,2], [0,3],
   * [1,0], [1,1], [1,2], [1,3],
   * [2,0], [2,1], [2,2], [2,3],
   * [3,0], [3,1], [3,2], [3,3].
   *
   * <p>
   * HINT: Use the methods of {@link LearningEnvironment} (implemented in
   * {@link Lab})
   * to interact with the learning environment (here, the lab), e.g., to retrieve
   * the
   * applicable actions, perform an action at the lab during learning etc.
   * </p>
   * 
   * @param goalDescription the desired goal against the which the Q matrix is
   *                        calculated (e.g., [2,3])
   * @param episodesObj     the number of episodes used for calculating the Q
   *                        matrix
   * @param alphaObj        the learning rate with range [0,1].
   * @param gammaObj        the discount factor [0,1]
   * @param epsilonObj      the exploration probability [0,1]
   * @param rewardObj       the reward assigned when reaching the goal state
   **/
  @OPERATION
  public void calculateQ(Object[] goalDescription, Object episodesObj, Object alphaObj, Object gammaObj,
      Object epsilonObj, Object rewardObj) {

    // ensure that the right datatypes are used
    Integer episodes = Integer.valueOf(episodesObj.toString());
    Double alpha = Double.valueOf(alphaObj.toString());
    Double gamma = Double.valueOf(gammaObj.toString());
    Double epsilon = Double.valueOf(epsilonObj.toString());
    Integer reward = Integer.valueOf(rewardObj.toString());
    Integer z1 = Integer.valueOf(goalDescription[0].toString());
    Integer z2 = Integer.valueOf(goalDescription[1].toString());

    this.qTables = readQTablesFromFile(FILENAME);

    String newKey = z1.toString() + z2.toString();

    if (this.qTables.containsKey(newKey)) {
      LOGGER
          .info("-------------------------------- oh, I already learnt this -----------------------------------------");
    } else {
      this.qTables.put(newKey, initializeQTable());
      double[][] thisVeryQTable = this.qTables.get(newKey);
      Integer currentState = this.lab.readCurrentState();
      Random random = new Random();
      for (int i = 0; i < episodes; i++) {
        LOGGER.info("-------------------------------- new Episode -----------------------------------------");
        // intialize S
        for (int j = 0; j < 1000; j++) {
          List<Integer> possibleActions = this.lab.getApplicableActions(currentState);
          int randomAction = possibleActions.get(random.nextInt(possibleActions.size()));
          this.lab.performAction(randomAction);

          try {
            Thread.sleep(3); // Sleep for 1000 milliseconds (1 second)
          } catch (InterruptedException e) {
            // Handle the exception if needed
          }
        }
        while (true) {
          List<Integer> possibleActions = this.lab.getApplicableActions(currentState);
          double randomNumber = random.nextDouble();
          int chosenAction = possibleActions.get(random.nextInt(possibleActions.size()));
          if (randomNumber > epsilon) {
            chosenAction = getMaxValueIndex(currentState, thisVeryQTable[currentState]);
          }
          this.lab.performAction(chosenAction);
          try {
            Thread.sleep(50); // Sleep for 1000 milliseconds (1 second)
          } catch (InterruptedException e) {
            // Handle the exception if needed
          }
          int newState = this.lab.readCurrentState();
          double maxqsda = getMaxQSA(newState, thisVeryQTable);
          double currentQsa = thisVeryQTable[currentState][chosenAction];
          int calculatedReward = checkforReward(goalDescription, reward, z1, z2);
          double newValue = currentQsa
              + alpha * ((calculatedReward + gamma * maxqsda) - currentQsa);
          // LOGGER.info("newValue: " + newValue);
          thisVeryQTable[currentState][chosenAction] = newValue;
          currentState = newState;
          if (calculatedReward == reward) {
            break;
          }
        }
      }
      LOGGER.info("-------------------------------- done -----------------------------------------");
    }

    writeQTablesToFile(qTables, FILENAME);
    //this.qTables = qTables;

    //double[][] usefulQTable = this.qTables.get(newKey);
    // printQTable(usefulQTable);
  }

      /**
     * selfmade
     * @return value to check whether we have reached the desired state
     */
    @OPERATION
    public void getCurrentState(OpFeedbackParam<Integer[]> currentStateTag) {
      int state1 = this.lab.getFullCurrentState().get(0);
      int state2 = this.lab.getFullCurrentState().get(1);
      Integer[] returner = new Integer[2];
      returner[0] = state1;
      returner[1] = state2;
      Integer currentState[] = returner;
      currentStateTag.set(currentState);
    }

     /**
     * selfmade
     * @return value to check whether we have reached the desired state
     */
    @OPERATION
    public void getCurrentFullState(OpFeedbackParam<Object[]> currentStateTag) {
      int state1 = this.lab.getFullCurrentState().get(0);
      int state2 = this.lab.getFullCurrentState().get(1);
      boolean state3 = this.lab.getFullCurrentState().get(2).equals(1);
      boolean state4 = this.lab.getFullCurrentState().get(3).equals(1);
      boolean state5 = this.lab.getFullCurrentState().get(4).equals(1);
      boolean state6 = this.lab.getFullCurrentState().get(5).equals(1);
      int state7 = this.lab.getFullCurrentState().get(6);


      Object[] returner = new Object[7];
      returner[0] = state1;
      returner[1] = state2;
      returner[2] = state3;
      returner[3] = state4;
      returner[4] = state5;
      returner[5] = state6;
      returner[6] = state7;
      Object currentState[] = returner;
      currentStateTag.set(currentState);
    }

  /**
   * selfmade
   */
  private static void writeQTablesToFile(HashMap<String, double[][]> qTables, String fileName) {
    try (ObjectOutputStream outputStream = new ObjectOutputStream(new FileOutputStream(fileName))) {
      outputStream.writeObject(qTables);
      System.out.println("QTables successfully written to file: " + fileName);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private static HashMap<String, double[][]> readQTablesFromFile(String fileName) {
    try (ObjectInputStream inputStream = new ObjectInputStream(new FileInputStream(fileName))) {
      HashMap<String, double[][]> qTables = (HashMap<String, double[][]>) inputStream.readObject();
      System.out.println("QTables successfully read from file: " + fileName);
      return qTables;
    } catch (IOException | ClassNotFoundException e) {

    }
    return new HashMap<>();
  }

  /**
   * selfmade
   */
  private int checkforReward(Object[] goalDescription, int reward, Integer z1, Integer z2) {
    Integer[] observedGoalDescription = this.lab.getPossibleGoalDescription();
    if (z1 == observedGoalDescription[0] && z2 == observedGoalDescription[1]) {
      LOGGER.info("++++++++++++++++++++++++++successful+++++++++++++++++++++++++++++");
      return reward;
    } else {
      return 0;
    }
  }

  /**
   * selfmade
   */
  private double getMaxQSA(int currentState,  double[][] qTable) {
    List<Integer> possibleActions = this.lab.getApplicableActions(currentState);
    double max = 0.0;
    for (int i = 0; i < possibleActions.size(); i++) {
      Integer possAct = possibleActions.get(i);
      double possibleMax = qTable[currentState][possAct];
      if (possibleMax > max) {
        max = possibleMax;
      }
    }
    return max;
  }

  /**
   * Returns information about the next best action based on a provided state and
   * the QTable for
   * a goal description. The returned information can be used by agents to invoke
   * an action
   * using a ThingArtifact.
   *
   * @param goalDescription           the desired goal against the which the Q
   *                                  matrix is calculated (e.g., [2,3])
   * @param currentStateDescription   the current state e.g.
   *                                  [2,2,true,false,true,true,2]
   * @param nextBestActionTag         the (returned) semantic annotation of the
   *                                  next best action, e.g.
   *                                  "http://example.org/was#SetZ1Light"
   * @param nextBestActionPayloadTags the (returned) semantic annotations of the
   *                                  payload of the next best action, e.g.
   *                                  [Z1Light]
   * @param nextBestActionPayload     the (returned) payload of the next best
   *                                  action, e.g. true
   **/
  @OPERATION
  public void getActionFromState(Object[] goalDescription, Object[] currentStateDescription,
      OpFeedbackParam<String> nextBestActionTag, OpFeedbackParam<Object[]> nextBestActionPayloadTags,
      OpFeedbackParam<Object[]> nextBestActionPayload) {

    Integer z1 = Integer.valueOf(goalDescription[0].toString());
    Integer z2 = Integer.valueOf(goalDescription[1].toString());
    String newKey = z1.toString() + z2.toString();

    // remove the following upon implementing Task 2.3!
    double[][] thisVeryQTable = this.qTables.get(newKey);
    Integer currentIndex = this.lab.readCurrentState();
    System.out.println("current Index: " + currentIndex);
    Random random = new Random();
    double randomNumber = random.nextDouble();
    double epsilon = 0.9;
    double[] possibleActions = thisVeryQTable[currentIndex];
    List<Integer> applicableActions = this.lab.getApplicableActions(currentIndex);
    
    int nextAction = applicableActions.get(random.nextInt(applicableActions.size()));
          if (randomNumber > epsilon) {
            nextAction = getMaxValueIndex(currentIndex, possibleActions);
          }
    switch (nextAction) {
      case 0:
        // Perform action for value 0
        // 0
        setZ1LightFalse(nextBestActionTag, nextBestActionPayloadTags, nextBestActionPayload);
        System.out.println("Performing action setZ1LightFalse");
        break;
      case 1:
        // Perform action for value 1
        // 1
        setZ1LightTrue(nextBestActionTag, nextBestActionPayloadTags, nextBestActionPayload);
        System.out.println("Performing action setZ1LightTrue");
        break;
      case 2:
        // Perform action for value 2
        // 2
        setZ2LightFalse(nextBestActionTag, nextBestActionPayloadTags, nextBestActionPayload);
        System.out.println("Performing action setZ2LightFalse");
        break;
      case 3:
        // Perform action for value 3
        // 3
        setZ2LightTrue(nextBestActionTag, nextBestActionPayloadTags, nextBestActionPayload);
        System.out.println("Performing action setZ2LightTrue");
        break;
      case 4:
        // Perform action for value 4
        // 4
        setZ1BlindsFalse(nextBestActionTag, nextBestActionPayloadTags, nextBestActionPayload);
        System.out.println("Performing action setZ1BlindsFalse");
        break;
      case 5:
        // Perform action for value 5

        // 5
        setZ1BlindsTrue(nextBestActionTag, nextBestActionPayloadTags, nextBestActionPayload);
        System.out.println("Performing action setZ1BlindsTrue");
        break;
      case 6:
        // Perform action for value 6
        // 6
        setZ2BlindsFalse(nextBestActionTag, nextBestActionPayloadTags, nextBestActionPayload);
        System.out.println("Performing action setZ2BlindsFalse");
        break;
      case 7:
        // Perform action for value 7
        // 7
        setZ2BlindsTrue(nextBestActionTag, nextBestActionPayloadTags, nextBestActionPayload);
        System.out.println("Performing action setZ2BlindsTrue");
        break;
      default:
        // Invalid index, output an error message
        System.err.println("Invalid next action index: " + nextAction);
        break;
    }

  }

  /**
   * self-made
   */

  private int getMaxValueIndex(Integer currentState, double[] possibleActions) {
    List<Integer> applicableActions = this.lab.getApplicableActions(currentState);
    Random random = new Random();
    int maxIndex = 0;

    double maxValue = possibleActions[applicableActions.get(0)];

    for (int i = 0; i < applicableActions.size(); i++) {
      double checkingValue = possibleActions[applicableActions.get(i)];
      if (possibleActions[applicableActions.get(i)] > maxValue) {
        maxValue = possibleActions[applicableActions.get(i)];
        maxIndex = applicableActions.get(i);
      }
    }
    if (maxValue == 0.0) {
      Integer maxIndexHelper = random.nextInt(applicableActions.size());
      maxIndex = applicableActions.get(maxIndexHelper);
    }
    return maxIndex;
  }


  /**
   * selfmade
   */
  private static String arrayToString(double[] array) {
    StringBuilder sb = new StringBuilder();
    for (double value : array) {
        sb.append(value).append(", ");
    }
    // Remove the trailing comma and space
    if (array.length > 0) {
        sb.setLength(sb.length() - 2);
    }
    return sb.toString();
}

  /**
   * self-made
   * 
   * @param nextBestActionTag
   * @param nextBestActionPayloadTags
   * @param nextBestActionPayload
   */
  private void setZ1LightTrue(OpFeedbackParam<String> nextBestActionTag,
      OpFeedbackParam<Object[]> nextBestActionPayloadTags,
      OpFeedbackParam<Object[]> nextBestActionPayload) {
    // sets the semantic annotation of the next best action to be returned
    nextBestActionTag.set("http://example.org/was#SetZ1Light");

    // sets the semantic annotation of the payload of the next best action to be
    // returned
    Object payloadTags[] = { "Z1Light" };
    nextBestActionPayloadTags.set(payloadTags);

    // sets the payload of the next best action to be returned
    Object payload[] = { true };
    nextBestActionPayload.set(payload);
  }

  /**
   * self-made
   * 
   * @param nextBestActionTag
   * @param nextBestActionPayloadTags
   * @param nextBestActionPayload
   */
  private void setZ1LightFalse(OpFeedbackParam<String> nextBestActionTag,
      OpFeedbackParam<Object[]> nextBestActionPayloadTags,
      OpFeedbackParam<Object[]> nextBestActionPayload) {
    // sets the semantic annotation of the next best action to be returned
    nextBestActionTag.set("http://example.org/was#SetZ1Light");

    // sets the semantic annotation of the payload of the next best action to be
    // returned
    Object payloadTags[] = { "Z1Light" };
    nextBestActionPayloadTags.set(payloadTags);

    // sets the payload of the next best action to be returned
    Object payload[] = { false };
    nextBestActionPayload.set(payload);
  }

  /**
   * self-made
   * 
   * @param nextBestActionTag
   * @param nextBestActionPayloadTags
   * @param nextBestActionPayload
   */
  private void setZ2LightFalse(OpFeedbackParam<String> nextBestActionTag,
      OpFeedbackParam<Object[]> nextBestActionPayloadTags,
      OpFeedbackParam<Object[]> nextBestActionPayload) {
    // sets the semantic annotation of the next best action to be returned
    nextBestActionTag.set("http://example.org/was#SetZ2Light");

    // sets the semantic annotation of the payload of the next best action to be
    // returned
    Object payloadTags[] = { "Z2Light" };
    nextBestActionPayloadTags.set(payloadTags);

    // sets the payload of the next best action to be returned
    Object payload[] = { false };
    nextBestActionPayload.set(payload);
  }

  /**
   * self-made
   * 
   * @param nextBestActionTag
   * @param nextBestActionPayloadTags
   * @param nextBestActionPayload
   */
  private void setZ2LightTrue(OpFeedbackParam<String> nextBestActionTag,
      OpFeedbackParam<Object[]> nextBestActionPayloadTags,
      OpFeedbackParam<Object[]> nextBestActionPayload) {
    // sets the semantic annotation of the next best action to be returned
    nextBestActionTag.set("http://example.org/was#SetZ2Light");

    // sets the semantic annotation of the payload of the next best action to be
    // returned
    Object payloadTags[] = { "Z2Light" };
    nextBestActionPayloadTags.set(payloadTags);

    // sets the payload of the next best action to be returned
    Object payload[] = { true };
    nextBestActionPayload.set(payload);
  }

  /**
   * self-made
   * 
   * @param nextBestActionTag
   * @param nextBestActionPayloadTags
   * @param nextBestActionPayload
   */
  private void setZ1BlindsTrue(OpFeedbackParam<String> nextBestActionTag,
      OpFeedbackParam<Object[]> nextBestActionPayloadTags,
      OpFeedbackParam<Object[]> nextBestActionPayload) {
    // sets the semantic annotation of the next best action to be returned
    nextBestActionTag.set("http://example.org/was#SetZ1Blinds");

    // sets the semantic annotation of the payload of the next best action to be
    // returned
    Object payloadTags[] = { "Z1Blinds" };
    nextBestActionPayloadTags.set(payloadTags);

    // sets the payload of the next best action to be returned
    Object payload[] = { true };
    nextBestActionPayload.set(payload);
  }

  /**
   * self-made
   * 
   * @param nextBestActionTag
   * @param nextBestActionPayloadTags
   * @param nextBestActionPayload
   */
  private void setZ1BlindsFalse(OpFeedbackParam<String> nextBestActionTag,
      OpFeedbackParam<Object[]> nextBestActionPayloadTags,
      OpFeedbackParam<Object[]> nextBestActionPayload) {
    // sets the semantic annotation of the next best action to be returned
    nextBestActionTag.set("http://example.org/was#SetZ1Blinds");

    // sets the semantic annotation of the payload of the next best action to be
    // returned
    Object payloadTags[] = { "Z1Blinds" };
    nextBestActionPayloadTags.set(payloadTags);

    // sets the payload of the next best action to be returned
    Object payload[] = { false };
    nextBestActionPayload.set(payload);
  }

  /**
   * self-made
   * 
   * @param nextBestActionTag
   * @param nextBestActionPayloadTags
   * @param nextBestActionPayload
   */
  private void setZ2BlindsFalse(OpFeedbackParam<String> nextBestActionTag,
      OpFeedbackParam<Object[]> nextBestActionPayloadTags,
      OpFeedbackParam<Object[]> nextBestActionPayload) {
    // sets the semantic annotation of the next best action to be returned
    nextBestActionTag.set("http://example.org/was#SetZ2Blinds");

    // sets the semantic annotation of the payload of the next best action to be
    // returned
    Object payloadTags[] = { "Z2Blinds" };
    nextBestActionPayloadTags.set(payloadTags);

    // sets the payload of the next best action to be returned
    Object payload[] = { false };
    nextBestActionPayload.set(payload);
  }

  /**
   * self-made
   * 
   * @param nextBestActionTag
   * @param nextBestActionPayloadTags
   * @param nextBestActionPayload
   */
  private void setZ2BlindsTrue(OpFeedbackParam<String> nextBestActionTag,
      OpFeedbackParam<Object[]> nextBestActionPayloadTags,
      OpFeedbackParam<Object[]> nextBestActionPayload) {
    // sets the semantic annotation of the next best action to be returned
    nextBestActionTag.set("http://example.org/was#SetZ2Blinds");

    // sets the semantic annotation of the payload of the next best action to be
    // returned
    Object payloadTags[] = { "Z2Blinds" };
    nextBestActionPayloadTags.set(payloadTags);

    // sets the payload of the next best action to be returned
    Object payload[] = { true };
    nextBestActionPayload.set(payload);
  }

  /**
   * Print the Q matrix
   *
   * @param qTable the Q matrix
   */
  void printQTable(double[][] qTable) {
    System.out.println("Q matrix");
    for (int i = 0; i < qTable.length; i++) {
      System.out.print("From state " + i + ":  ");
      for (int j = 0; j < qTable[i].length; j++) {
        System.out.printf("%6.2f ", (qTable[i][j]));
      }
      System.out.println();
    }
  }

  /**
   * Initialize a Q matrix
   *
   * @return the Q matrix
   */
  private double[][] initializeQTable() {
    double[][] qTable = new double[this.stateCount][this.actionCount];
    for (int i = 0; i < stateCount; i++) {
      for (int j = 0; j < actionCount; j++) {
        qTable[i][j] = 0.0;
      }
    }
    return qTable;
  }
}
