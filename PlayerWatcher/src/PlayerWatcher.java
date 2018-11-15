//import com.sun.javaws.Main;
import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;




class ZooKeeperConnection {
    private ZooKeeper zoo;

    final CountDownLatch connectedSignal = new CountDownLatch(1);

    public ZooKeeper connect(String host) throws IOException, InterruptedException {
        zoo = new ZooKeeper(host, 500000, new Watcher() {
            @Override
            public void process(WatchedEvent event) {
                if(event.getState()== Event.KeeperState.SyncConnected){
                    connectedSignal.countDown();
                }
            }
        });

        connectedSignal.await();

        return zoo;
    }

    public void close() throws  InterruptedException {
        zoo.close();
    }
}



public class PlayerWatcher implements Watcher{

    private static ZooKeeper zk;

    private static ZooKeeperConnection connection;

    final static Logger logger = LoggerFactory.getLogger(PlayerWatcher.class);

    int numberOfEvents ;


    @Override
    public void process(WatchedEvent event) {
        try{
            if(event.getType().equals(Event.EventType.NodeDataChanged) || event.getType().equals(Event.EventType.NodeChildrenChanged)
                    || event.getType().equals(Event.EventType.NodeCreated) || event.getType().equals(Event.EventType.NodeDeleted))
            {
                byte[] data = new byte[0];
                try {
                    data = zk.getData(event.getPath(), this, new Stat());
                    printPlayers(numberOfEvents);
                    zk.getChildren(event.getPath(), this);
                    printPlayers(numberOfEvents);
                } catch (KeeperException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        catch (Exception e)
        {
            logger.error("Exception in processing the watch.", e);
        }
    }


    public static void create(String path, byte[] data) throws KeeperException, InterruptedException
    {
        zk.create(path, data, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
    }

    public static Stat znode_exists(String path) throws KeeperException, InterruptedException
    {
        return zk.exists(path, true);
    }

    public static void delete(String path) throws KeeperException,InterruptedException {
        zk.delete(path,zk.exists(path,true).getVersion());
    }

    public static void update(String path, byte[] data) throws KeeperException, InterruptedException
    {
        zk.setData(path, data, zk.exists(path, true).getVersion());
    }

    public static List<String> getChildren(String path) throws KeeperException, InterruptedException {
        return zk.getChildren(path,false);
    }

    public static boolean checkOnline(String path) throws KeeperException, InterruptedException, UnsupportedEncodingException {

        Stat stat = znode_exists("/active");

        String activeNode = splitStr(path);

        if (stat!=null)
        {
            byte[] b = zk.getData("/active",true,stat);
            String active_player_List = new String(b,"UTF-8");
            if (active_player_List.indexOf(activeNode)!=-1)
            {
                return true;
            }
        }

        return false;
    }

    public static String splitStr(String path)
    {
        String[] str = path.split("/",-2);
        return str[1];
    }

    public static void printPlayers(int noOfScores) throws KeeperException, InterruptedException, UnsupportedEncodingException {

        List<String> currentPlayers = getChildren("/");

        Map<String, Double> scoreHashMap = new HashMap<>();

        Map<String, Long> timeHashMap = new HashMap<>();

        for (String player: currentPlayers) {
            String path = "/" + player;
            if (player.equalsIgnoreCase("zookeeper") || player.equalsIgnoreCase("active")) {
                continue;
            } else{
                List<String> childrensOfPlayers = getChildren(path);
                for (String child: childrensOfPlayers) {
                    String childPath = path+"/"+child;
                    Stat stat = znode_exists(childPath);
                    byte[] childBytes = zk.getData(childPath, true, new Stat());
                    try {
                        String childString = new String(childBytes,"UTF-8");
                        if(childString.equalsIgnoreCase("") || childString==null)
                        {
                            continue;
                        }
                        else {
                            Double scoreValueChild = Double.parseDouble(childString);
                            scoreHashMap.put(childPath,scoreValueChild); // stores node and score
                            timeHashMap.put(childPath,stat.getCtime()); // stores node and ctime
                        }


                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                } // for loop for child
            }

        } // for loop for player




        Map<String, Long> sortedTimeHashMap = timeHashMap.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (oldValue, newValue) -> oldValue, LinkedHashMap::new));

        Map<String, Double> sortedScoreHashMap = scoreHashMap.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (oldValue, newValue) -> oldValue, LinkedHashMap::new));

        int countOfRecent = noOfScores, countOfHighest = noOfScores;

        System.out.println("\n\t\t\t\t Most Recent Scores \n \t\t ------------------------------");
        for(Map.Entry<String,Long> entry : sortedTimeHashMap.entrySet())
        {
            if(countOfRecent==0)
                break;
            else
            {
                if(sortedTimeHashMap.containsKey(entry.getKey()))
                {
                    if(checkOnline(entry.getKey()))
                    {
                        System.out.println(splitStr(entry.getKey())+ "\t" + sortedScoreHashMap.get(entry.getKey()) + "**");
                    }
                    else
                    {
                        System.out.println(splitStr(entry.getKey())+ "\t" + sortedScoreHashMap.get(entry.getKey()));
                    }
                }
                countOfRecent--;
            }
        } /// printing of most recent scores is done

        System.out.println("\n\t\t\t\t Highest Scores \n \t\t ------------------------------");
        for(Map.Entry<String,Double> entry : sortedScoreHashMap.entrySet())
        {
            if(countOfHighest==0)
                break;
            else
            {

                if(checkOnline(entry.getKey()))
                {
                    System.out.println(splitStr(entry.getKey())+ "\t" + sortedScoreHashMap.get(entry.getKey()) + "**");
                }
                else
                {
                    System.out.println(splitStr(entry.getKey())+ "\t" + sortedScoreHashMap.get(entry.getKey()));
                }

                countOfHighest--;
            }
        } /// printing of most recent scores is done


    } // printPlayer function ends




    public static void main(String[] args) {

        //String playerName = args[0];

        String hostIP = args[0];

        String numberOfScores = args[1];

        //System.out.println(hostIP);
        //System.out.println(numberOfScores);

        try{
            connection = new ZooKeeperConnection();

            zk = connection.connect(hostIP);

            PlayerWatcher w = new PlayerWatcher();


            w.numberOfEvents = Integer.parseInt(numberOfScores);

            //************** Watcher Code Starts********************//

            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    System.out.println("Watcher logging out...");
                    try {
                        connection.close();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                }
            });


            while(true) {

                if(null != zk.exists("/", w)) {
                    List<String> children = zk.getChildren("/", w);
                    for (String child : children) {
                        zk.exists("/"+child, w);
                        List<String> children2 = zk.getChildren("/"+child, w);
                        for (String child1 : children2) {
                            zk.exists("/"+child+"/"+child1, w);
                        }
                    }
                }

            }


            //************** Watcher Code Ends********************//
        }
        catch (Exception e)
        {
            System.out.println(e.getMessage());
        }
    }

}
