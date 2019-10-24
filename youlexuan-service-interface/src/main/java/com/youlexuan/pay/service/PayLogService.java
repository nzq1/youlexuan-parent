package com.youlexuan.pay.service;

import com.youlexuan.pojo.TbPayLog;

public interface PayLogService {
    public TbPayLog searchPayLogFromRedis(String userId);

    public void updateOrderStatus(String out_trade_no,String transaction_id);
}
