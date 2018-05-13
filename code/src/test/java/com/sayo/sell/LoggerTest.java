package com.sayo.sell;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * Created by Shuangyao
 * 15:47 2018/5/13
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@Slf4j
public class LoggerTest {

    Logger logger = LoggerFactory.getLogger(LoggerTest.class);

    @Test
    public void test1() {
        logger.info("Info...");
        logger.debug("debug...");
        logger.error("error...");
    }
}
