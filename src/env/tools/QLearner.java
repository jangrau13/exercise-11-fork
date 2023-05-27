package tools;

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
  private Lab lab; // the lab environment that will be learnt
  private int stateCount; // the number of possible states in the lab environment
  private int actionCount; // the number of possible actions in the lab environment
  private HashMap<String, double[][]> qTables = new HashMap<>(); // a map for storing the qTables computed for
                                                                   // different goals

  private static final Logger LOGGER = Logger.getLogger(QLearner.class.getName());

  public void init(String environmentURL) {

    // the URL of the W3C Thing Description of the lab Thing
    this.lab = new Lab(environmentURL);

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
    

    this.qTables.put(z1.toString() + z2.toString(), initializeQTable());
    double[][] thisVeryQTable = this.qTables.get(z1.toString() + z2.toString());
    Integer currentState = this.lab.readCurrentState();
    Random random = new Random();
    for (int i = 0; i < episodes; i++) {
      LOGGER.info("-------------------------------- new Episode -----------------------------------------");
      //intialize S 
      for (int j = 0; j < 1000; j++) {
        List<Integer> possibleActions = this.lab.getApplicableActions(currentState);
        int randomAction = possibleActions.get(random.nextInt(possibleActions.size()));
        this.lab.performAction(randomAction);

          try {
            Thread.sleep(10); // Sleep for 1000 milliseconds (1 second)
        } catch (InterruptedException e) {
            // Handle the exception if needed
        }
      }
      Integer[] observedGoalDescription = this.lab.getPossibleGoalDescription();
      while (true) {
        List<Integer> possibleActions = this.lab.getApplicableActions(currentState);
        int randomAction = possibleActions.get(random.nextInt(possibleActions.size()));
        this.lab.performAction(randomAction);
        try {
          Thread.sleep(10); // Sleep for 1000 milliseconds (1 second)
      } catch (InterruptedException e) {
          // Handle the exception if needed
      }
        int newState = this.lab.readCurrentState();
        double maxqsda = getMaxQSA(newState, randomAction, thisVeryQTable);
        double currentQsa = thisVeryQTable[currentState][randomAction];
        int calculatedReward = checkforReward(goalDescription, reward, z1, z2);
        double newValue = currentQsa
            + alpha * ((calculatedReward + gamma * maxqsda) - currentQsa);
        //LOGGER.info("newValue: " + newValue);
        thisVeryQTable[currentState][randomAction] = newValue;
        currentState = newState;
        observedGoalDescription = this.lab.getPossibleGoalDescription();
        if (calculatedReward == reward) {
          break;
        }
      }
    }
    LOGGER.info("-------------------------------- done -----------------------------------------");
    //printQTable(thisVeryQTable);
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
  private double getMaxQSA(int currentState, int action, double[][] qTable) {
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

    // remove the following upon implementing Task 2.3!
    double[][] thisVeryQTable = this.qTables.get(z1.toString() + z2.toString());

    

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
