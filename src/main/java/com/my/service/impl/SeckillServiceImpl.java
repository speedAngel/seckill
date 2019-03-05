package com.my.service.impl;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.springframework.util.DigestUtils;
import org.springframework.transaction.annotation.Transactional;


import com.my.dao.SeckillMapper;
import com.my.dao.SuccessKilledMapper;
import com.my.dao.cache.RedisDao;
import com.my.dto.Exposer;
import com.my.dto.SeckillExecution;
import com.my.entity.Seckill;
import com.my.entity.SuccessKilled;
import com.my.enums.SeckillStatEnum;
import com.my.exception.RepeatKillException;
import com.my.exception.SeckillCloseException;
import com.my.exception.SeckillException;

import com.my.service.SeckillService;

import java.util.Date;
import java.util.List;

/**
 * 
 */

//@Component @Service @Dao @Controller
@Service
public class SeckillServiceImpl implements SeckillService {

    //日志对象
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    //加入一个混淆字符串(秒杀接口)的盐值，为避免用户猜出md5值，值任意给，越复杂越好
    private final String slat = "vnosdpowsmb%$^&*$^%&*sakvdSDHBHDNojn!!";

    //注入Service依赖
    @Autowired //@Resource
    private SeckillMapper seckillMapper;

    @Autowired //@Resource
    private SuccessKilledMapper successKilledMapper;
    @Autowired
    private  RedisDao redisDao;
    

    public List<Seckill> getSeckillList() {

        return seckillMapper.queryAll(0, 4);
    }

    public Seckill getById(long seckillId) {

        return seckillMapper.queryById(seckillId);
    }

    public Exposer exportSeckillUrl(long seckillId) {

        /**
                          * 优化点：缓存优化：超时的基础上维护一致性
         */

        //1:访问redis
        Seckill seckill = redisDao.getSeckill(seckillId);
        System.out.println("getSeckill   "+seckill + "id- " +seckillId);

        if (seckill == null) {
            //2:访问数据库
            seckill = seckillMapper.queryById(seckillId);

            //查询不到秒杀产品记录
            if (seckill == null) {
                return new Exposer(false, seckillId);
            } else {
                //3:放入redis
                redisDao.putSeckill(seckill);
                System.out.println("putSeckill   "+seckill + "id- " +seckillId);
            }
        }


        Date startTime = seckill.getStartTime();
        Date endTime = seckill.getEndTime();
        Date nowTime = new Date();

        //还未到秒杀时间，或者已经过秒杀时间
        if (nowTime.getTime() < startTime.getTime() || nowTime.getTime() > endTime.getTime()) {
            return new Exposer(false, seckillId, nowTime.getTime(), startTime.getTime(), endTime.getTime());
        }

        //转换特定字符串的过程，不可逆
        String md5 = getMD5(seckillId);
        return new Exposer(true, md5, seckillId);
    }

    private String getMD5(long seckillId) {
        String base = seckillId + "/" + slat;
        String md5 = DigestUtils.md5DigestAsHex(base.getBytes());
        return md5;
    }

    @Transactional
    /**
     * 使用注解控制事务方法的优点：
     * 1：开发团队达成一致约定，明确标注事务方法的编程风格
     * 2：保证事务方法的执行时间尽可能短，不要穿插其它网络操作，RPC/HTTP请求或者剥离到事务方法外部
     * 3：不是所有的方法都需要事务，如只有一条修改操作，只读操作不需要事务控制
     */
    public SeckillExecution executeSeckill(long seckillId, long userPhone, String md5)
            throws SeckillException, RepeatKillException, SeckillCloseException {

        if (md5 == null || !md5.equals(getMD5(seckillId))) {
            throw new SeckillException("seckill data rewrite");

        }

        //执行秒杀逻辑：（1）减库存，（2）记录购买行为

        Date nowTime = new Date();

        //try catch的原因，可能出现插入数据超时，或者数据库连接中断异常
        try {

            //记录购买行为
            int insertCount = successKilledMapper.insertSuccessKilled(seckillId, userPhone);
            //唯一：seckillId，userPhone
            if (insertCount <= 0) {
                //重复秒杀
                throw new RepeatKillException("seckill repeated");
            } else {

                //减库存
                int updateCount = seckillMapper.reduceNumber(seckillId, nowTime);
                if (updateCount <= 0) {
                    //没有更新到记录，秒杀结束，rollback
                    throw new SeckillCloseException("seckill is closed");
                } else {

                    //秒杀成功
                    SuccessKilled successKilled = successKilledMapper.queryByIdWithSeckill(seckillId, userPhone);

                    return new SeckillExecution(seckillId, SeckillStatEnum.SUCCESS, successKilled);
                }

            }


            //为了能报出精确的异常，因此先catch其它异常，再catch exception结束
        } catch (SeckillCloseException e1) {
            throw e1;
        } catch (RepeatKillException e2) {
            throw e2;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            //所有编译异常转化为运行期异常
            throw new SeckillException("seckill inner error:" + e.getMessage());
        }

    }
}
