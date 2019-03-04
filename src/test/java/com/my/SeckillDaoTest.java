package com.my;

import org.junit.Test;
import org.junit.runner.RunWith;


import com.my.dao.SeckillMapper;
import com.my.entity.Seckill;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

/**
 * Created by joryun on 2017/4/12.
 *
 * 配置spring和junit整合，junit启动时加载springIOC容器
 * spring-test,junit
 */

@RunWith(SpringRunner.class)
@SpringBootTest
public class SeckillDaoTest {

    //注入Dao实现类依赖
    @Resource
    private SeckillMapper seckillMapper;

    @Test
    public void testQueryById() throws Exception {
        long id = 1000;
        Seckill seckill = seckillMapper.queryById(id);
        System.out.println(seckill.getName());
        System.out.println(seckill);
    }

    @Test
    public void testQueryAll() throws Exception {

        /**
         * junit测试不通过原因：
         * java没有保存形参的记录：queryAll(int offet, int limit) -> queryAll(arg0, arg1)
         */

        List<Seckill> seckills = seckillMapper.queryAll(0, 100);
        for (Seckill seckill : seckills){
            System.out.println(seckill);
        }
    }

    @Test
    public void testReduceNumber() throws Exception {
        Date killTime = new Date();
        int updateCount = seckillMapper.reduceNumber(1000L, killTime);
        System.out.println("updateCount="+ updateCount);
    }

}