import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;


class PlayerJoinFailException extends Exception {
    public PlayerJoinFailException(String message) {
        super(message);
    }
}

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


public class Player {

    private static ZooKeeper zk;

    private static ZooKeeperConnection connection;

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

    public static void addActive(ZooKeeper zoo, ZooKeeperConnection conn,String name) throws KeeperException, InterruptedException {
        Stat stat = znode_exists("/active");
        try{
            if(stat!=null) {
                byte[] b = zk.getData("/active",true,stat);
                String active_player_List = new String(b,"UTF-8");
                String new_player_List = new String("".getBytes(),"UTF-8");
                if(active_player_List.indexOf(name)==-1)
                {
                    new_player_List= active_player_List+","+name;
                }
                update("/active",new_player_List.getBytes());
            }
            else {
                create("/active",name.getBytes());
            }
        }
        catch (Exception e)
        {
            System.out.println(e.getMessage());
        }

    }

    public static void removeActive(ZooKeeper zoo, ZooKeeperConnection conn,String name) throws KeeperException, InterruptedException{
        Stat stat = znode_exists("/active");

        try {
            if(stat!=null)
            {
                byte[] b = zk.getData("/active",true,stat);
                String active_player_List = new String(b,"UTF-8");

                String new_player_List = new String("".getBytes(),"UTF-8");


                if(active_player_List.indexOf(name)!=-1)
                {
                    new_player_List= active_player_List.replace(name,"");
                }

                update("/active",new_player_List.getBytes());
            }
        }
        catch (Exception e)
        {
            System.out.println(e.getMessage());
        }
    }


    public static void checkJoinFail(ZooKeeper zoo, ZooKeeperConnection conn,String name) throws KeeperException, InterruptedException, UnsupportedEncodingException {

        Stat stat = znode_exists("/active");

        if (stat!=null)
        {
            byte[] b = zk.getData("/active",true,stat);
            String active_player_List = new String(b,"UTF-8");
            if (active_player_List.indexOf(name)!=-1)
            {
                try {
                    throw new PlayerJoinFailException("Player is online, cannot join again");
                } catch (PlayerJoinFailException e) {
                    System.out.println(e.getMessage());
                    System.exit(1);
                }
            }

        }

    }




    public static void main(String[] args)
    {
        final String playerName = args[1];

        String hostIP = args[0];

        try{
            connection = new ZooKeeperConnection();

            zk = connection.connect(hostIP);

            Player p = new Player();


            checkJoinFail(zk,connection,playerName);

            addActive(zk,connection,playerName);

            if(args.length<3)
            {

                Runtime.getRuntime().addShutdownHook(new Thread()
                {
                    @Override
                    public void run()
                    {
                        try {
                            System.out.println("Player logging out...");
                            Player.removeActive(zk,connection,playerName);
                        } catch (KeeperException e) {
                            e.printStackTrace();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                });


                String name = args[1];

                System.out.println(name + " is online now");

                String path = "/"+name;

                Scanner src = new Scanner(System.in);

                String score;


                try{

                    while(true)
                    {
                        Stat stat = znode_exists(path);

                        if(stat!=null)
                        {
                            score = src.nextLine();

                            double temp = Double.parseDouble(score);
                            if(temp <0 || temp >Integer.MAX_VALUE)
                            {
                                System.out.println("Score not valid.. Please re-enter the value...");
                                continue;
                            }
                            String enterScore = new String(score.getBytes(),"UTF-8");
                            List<String> children = getChildren(path);
                            create(path+"/"+(children.size()+1),score.getBytes());
                        }
                        else {
                            score = src.nextLine();

                            double temp = Double.parseDouble(score);
                            if(temp <0 || temp >Integer.MAX_VALUE)
                            {
                                System.out.println("Score not valid.. Please re-enter the value...");
                                continue;
                            }
                            String enterScore = new String(score.getBytes(),"UTF-8");
                            create(path,"0".getBytes());
                            create(path+"/1",enterScore.getBytes());
                        }

                        System.out.println("Score :"+score);

                    }

                }
                catch (InterruptedException ie)
                {
                    removeActive(zk,connection,playerName);
                    System.out.println("Player Exited");
                }
                catch (KeeperException ke)
                {
                    System.out.println(ke.getMessage());
                }
                catch (NumberFormatException nfe)
                {
                    System.out.println(nfe.getMessage());
                }
                catch(Exception e) {
                    System.out.println(e.getMessage());
                }

            }
            else
            {
                String name = args[1];
                //String count = args[2];
                //String delay = args[3];
                //String score = args[4];

                Double delay = Double.parseDouble(args[3]);
                Double score = Double.parseDouble(args[4]);
                int count = Integer.parseInt(args[2]);

                System.out.println(name + " is online now");

                String path = "/"+name;

                Scanner src = new Scanner(System.in);

                int ctr = count;

                Random r = new Random();

                List<Double> delayList = new ArrayList<>();
                List<Double> scoreList = new ArrayList<>();

                String tempScore;
                try {

                    for (int i = 0; i < count; i++) {
                        delayList.add(r.nextGaussian()*0.1+delay);
                        scoreList.add(r.nextGaussian()*2000+score);
                    }

                    while (ctr>0)
                    {
                        Stat stat = znode_exists(path);
                        //Thread.sleep(Long.parseLong(String.valueOf(delayList.get(ctr-1)))*1000);
                        Thread.sleep((long)(delayList.get(ctr-1)*1000));

                        if(stat!=null)
                        {
                            //score = new String(src.next().getBytes(),"UTF-8");
                            //create("/"+name+"/1",(""+scoreList.get(ctr)).getBytes());
                            //String enterScore = new String(score.getBytes(),"UTF-8");
                            List<String> children = getChildren("/"+name);
                            tempScore = new String((""+(scoreList.get(ctr-1))).getBytes(),"UTF-8");
                            create("/"+name+"/"+(children.size()+1),tempScore.getBytes());
                        }
                        else
                        {
                            //score = new String(src.next().getBytes(),"UTF-8");
                            tempScore = new String((""+scoreList.get(ctr-1)).getBytes(),"UTF-8");
                            create(path,"0".getBytes());
                            create(path+"/1",tempScore.getBytes());
                        }
                        System.out.println("Score :"+tempScore);

                        ctr--;
                    }

                    removeActive(zk,connection,playerName);
                }
                catch (InterruptedException ie)
                {
                    System.out.println("Player exited");
                }
                catch (KeeperException ke)
                {
                    System.out.println(ke.getMessage());
                }

            }

            removeActive(zk,connection,playerName);

            connection.close();

        }
        catch (Exception e)
        {
            System.out.println(e.getMessage());
        }

    }


}

