package com.youlexuan.cart.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.youlexuan.entity.Result;
import com.youlexuan.order.service.OrderService;
import com.youlexuan.pay.service.AliPayService;
import com.youlexuan.pay.service.PayLogService;
import com.youlexuan.pojo.TbPayLog;
import com.youlexuan.seckill.service.SeckillOrderService;
import com.youlexuan.util.IdWorker;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/pay")
public class PayController {
    @Reference
    private AliPayService aliPayService;

    @Reference
    private OrderService orderService;

    @Reference
    private PayLogService payLogService;

    @Reference
    private SeckillOrderService seckillOrderService;

    /**
     * 生成二维码
     * @return
     */
    @RequestMapping("/createNative")
    public Map createNative(){
        //获取当前用户
        String userId=SecurityContextHolder.getContext().getAuthentication().getName();
        //到redis查询支付日志
        TbPayLog payLog = payLogService.searchPayLogFromRedis(userId);
        //判断支付日志存在
        if(payLog!=null){
            return aliPayService.createNative(payLog.getOutTradeNo(),payLog.getTotalFee()+"");
        }else{
            return new HashMap();
        }
    }

   /* @RequestMapping("/createNative")
    public Map createNative(){
        IdWorker idworker=new IdWorker();
        return aliPayService.createNative(idworker.nextId()+"","1");
    }*/

    @RequestMapping("/queryPayStatus")
    public Result queryPayStatus(String out_trade_no){
        String userId=SecurityContextHolder.getContext().getAuthentication().getName();
        Result result=null;
        int x=0;
        while(true){
            //调用查询接口
            Map<String, String> map = null;
            try {
                map = aliPayService.queryPayStatus(out_trade_no);

            } catch (Exception e1) {
                /*e1.printStackTrace();*/
                System.out.println("调用查询服务出错");
            }
            if(map==null){//出错
                result=new  Result(false, "支付出错");
                break;
            }
            if(map.get("tradestatus")!=null&&map.get("tradestatus").equals("TRADE_SUCCESS")){//如果成功
                result=new  Result(true, "支付成功");
                payLogService.updateOrderStatus(out_trade_no, map.get("trade_no"));
                break;
            }
            if(map.get("tradestatus")!=null&&map.get("tradestatus").equals("TRADE_CLOSED")){//如果成功
                result=new  Result(true, "未付款交易超时关闭，或支付完成后全额退款");
                break;
            }
            if(map.get("tradestatus")!=null&&map.get("tradestatus").equals("TRADE_FINISHED")){//如果成功
                result=new  Result(true, "交易结束，不可退款");
                payLogService.updateOrderStatus(out_trade_no, map.get("trade_no"));
                break;
            }
            try {
                Thread.sleep(3000);//间隔三秒
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            //为了不让循环无休止地运行，我们定义一个循环变量，如果这个变量超过了这个值则退出循环，设置时间为5分钟
            x++;
            if(x>=100){
                result=new  Result(false, "超过时间未支付,订单取消");
                //1.调用支付宝的关闭订单接口（学员实现）
                Map<String,String> payresult = aliPayService.closePay(out_trade_no);
                if("10000".equals(payresult.get("code")) ){//如果返回结果是正常关闭
                    System.out.println("超时，取消订单");
                    //2.调用删除
                    seckillOrderService.deleteOrderFromRedis(userId, Long.valueOf(out_trade_no));
                }
                break;
            }
        }
        return result;
    }
}












