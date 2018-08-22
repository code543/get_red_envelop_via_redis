package com.redenvelop;

import redis.clients.jedis.Jedis;
import java.util.*;

public class RedEnvelope {

    final String channel = "red_envelop_channel";


    static String[] names = {"Rhine", "Fate", "Fong", "Frank", "Ferro", "Rex"
                            , "Charles", "Santos", "James", "Michael", "Howard", "Joe", "PC"
                            , "Tim", "Jamie", "Jeff", "Kevin", "Jay", "Ken", "Peter", "Josh"
                            , "Soar", "Keith", "Claudia", "Katrina", "Allen", "Jerry", "Kyle", "Derek"};

    public static void main(String[] args)
    {
        new RedEnvelope().startRedEnvelope();
    }

    public void startRedEnvelope()
    {
        new Thread(()->{
                GiveRedEnvelope giveClient;
                //分配紅包
                giveClient = new GiveRedEnvelope();
                giveClient.give(300, (int)(names.length * 0.5));
                //開始搶
                //shuffle name list
                List<String> n = Arrays.asList(names);
                Collections.shuffle(n);
                //to wait everyone finish..simple
                //start n thread
                for(int index=0;index<n.size();index++)
                {
                    final int i = index;
                    new Thread(()-> {
                                  new GetRedEnvelope(n.get(i)).get();
                                }
                              ).start();
                }
                //timeout 30 sec, 等待最後一包~~
                boolean finish = false;
                while (!finish) {
                    try {
                        giveClient.jedis.brpop(30, channel);
                        //last one push a message, check...
                        int count = Integer.parseInt(giveClient.jedis.get("red_envelop_count"));
                        System.out.println("檢查目前狀況 紅包個數 " + count);
                        if (count <= 0) {
                            System.out.println("搶紅包結束~~");
                            finish = true;
                        }
                    } catch (Exception e) {
                    }
                }
                //get result
                System.out.println("得獎名單");
                Map<String, String> rewards = giveClient.jedis.hgetAll("red_envelop_reward");
                for(String name : rewards.keySet())
                {
                    System.out.println(name + " : " + rewards.get(name));
                }

                giveClient.jedis.close();
            }
        ).start();
    }



    public class GiveRedEnvelope
    {
        Jedis jedis;

        public void give(int money, int count)
        {
            jedis = new Jedis("localhost");
            System.out.println("分配紅包~~~$" + money + ", 共 " + count + "份");

            ArrayList<String> moneyList = new ArrayList<String>();
            long lastMoney = money*100;
            for(int index=0;index<count;index++)
            {
                long currentMoney;
                if(index == count - 1)
                {
                    currentMoney = lastMoney;
                }
                else {
                    currentMoney = Math.round(Math.random() * lastMoney * 0.4f + lastMoney * 0.1f);
                }
                //shuffle envelop list
                Collections.shuffle(moneyList);
                moneyList.add(String.valueOf(currentMoney));
                System.out.println("紅包第" + index + "包 $" + currentMoney/100.0);
                lastMoney -= currentMoney;

            }
            //驗證
            long sum = 0;
            for(int index=0;index<count;index++){
                sum += Long.parseLong(moneyList.get(index));
            }

            System.out.println("驗證紅包$" + sum/100.0 + " total $" + (money) + "\n\n");

            //put to redis
            jedis.del("red_envelop");
            jedis.del("red_envelop_count");
            jedis.del("red_envelop_reward");
            for(int index=0;index<count;index++){
                jedis.rpush("red_envelop", moneyList.get(index));
            }
            jedis.set("red_envelop_count", String.valueOf(count));
        }
    }


    public class GetRedEnvelope
    {
        Jedis jedis;
        String name;
        long myIndex;


        public GetRedEnvelope(String n)
        {
            name = n;
        }

        public void get()
        {
            //System.out.println(name + " 搶紅包:開始~~");
            jedis = new Jedis("localhost");
            //atomic decr
            myIndex  = jedis.decr("red_envelop_count");
            long money = 0;
            String moneyStr = jedis.lindex("red_envelop", myIndex);
            if(myIndex >= 0) money = Long.parseLong(moneyStr);
            if(myIndex >= 0) {
                jedis.hset("red_envelop_reward", name, String.valueOf(money / 100.0));
                //System.out.println(name + " 搶紅包:第 " + myIndex + " 包, $" + money / 100.0);
                System.out.println(name + " 搶到紅包" + " $" + money / 100.0);
                if(myIndex == 0)
                {
                    jedis.lpush(channel, "0");
                }
            }

            jedis.close();
        }
    }



}
