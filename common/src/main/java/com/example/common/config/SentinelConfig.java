package com.example.common.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * Sentinel限流熔断配置
 * 实现高可用保护机制
 */
@Configuration
public class SentinelConfig {

    @PostConstruct
    public void initSentinelRules() {
        initFlowRules();
        initDegradeRules();
    }

    /**
     * 初始化限流规则
     */
    private void initFlowRules() {
        List<FlowRule> rules = new ArrayList<>();

        // 用户服务限流规则
        FlowRule userRule = new FlowRule();
        userRule.setResource("user-service");
        userRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        userRule.setCount(100); // 每秒100个请求
        rules.add(userRule);

        // 订单服务限流规则
        FlowRule orderRule = new FlowRule();
        orderRule.setResource("order-service");
        orderRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        orderRule.setCount(50); // 每秒50个请求
        rules.add(orderRule);

        // 支付服务限流规则
        FlowRule paymentRule = new FlowRule();
        paymentRule.setResource("payment-service");
        paymentRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        paymentRule.setCount(30); // 每秒30个请求
        rules.add(paymentRule);

        // 数据库查询限流规则
        FlowRule dbRule = new FlowRule();
        dbRule.setResource("database-query");
        dbRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        dbRule.setCount(200); // 每秒200个查询
        rules.add(dbRule);

        FlowRuleManager.loadRules(rules);
    }

    /**
     * 初始化熔断规则
     */
    private void initDegradeRules() {
        List<DegradeRule> rules = new ArrayList<>();

        // 用户服务熔断规则
        DegradeRule userDegradeRule = new DegradeRule();
        userDegradeRule.setResource("user-service");
        userDegradeRule.setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_COUNT);
        userDegradeRule.setCount(5); // 5个异常后熔断
        userDegradeRule.setTimeWindow(10); // 熔断10秒
        userDegradeRule.setMinRequestAmount(10); // 最少10个请求才统计
        rules.add(userDegradeRule);

        // 订单服务熔断规则
        DegradeRule orderDegradeRule = new DegradeRule();
        orderDegradeRule.setResource("order-service");
        orderDegradeRule.setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO);
        orderDegradeRule.setCount(0.5); // 50%异常率后熔断
        orderDegradeRule.setTimeWindow(10);
        orderDegradeRule.setMinRequestAmount(5);
        rules.add(orderDegradeRule);

        // 支付服务熔断规则
        DegradeRule paymentDegradeRule = new DegradeRule();
        paymentDegradeRule.setResource("payment-service");
        paymentDegradeRule.setGrade(RuleConstant.DEGRADE_GRADE_RT);
        paymentDegradeRule.setCount(1000); // 平均响应时间超过1秒熔断
        paymentDegradeRule.setTimeWindow(10);
        paymentDegradeRule.setMinRequestAmount(5);
        rules.add(paymentDegradeRule);

        // 数据库熔断规则
        DegradeRule dbDegradeRule = new DegradeRule();
        dbDegradeRule.setResource("database-query");
        dbDegradeRule.setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_COUNT);
        dbDegradeRule.setCount(10);
        dbDegradeRule.setTimeWindow(30);
        dbDegradeRule.setMinRequestAmount(20);
        rules.add(dbDegradeRule);

        DegradeRuleManager.loadRules(rules);
    }
}