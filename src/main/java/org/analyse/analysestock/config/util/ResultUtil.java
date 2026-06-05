package org.analyse.analysestock.config.util;

import org.analyse.analysestock.config.ResultData;
import org.analyse.analysestock.config.enu.CodeMsg;
import org.analyse.analysestock.config.exception.CommonException;

import java.util.HashMap;

/**
 * @author dengzhiqiang
 * @date 2021/11/25
 * @describe
 */
public class ResultUtil {
    /**
     * 1 执行成功
     *
     * @param t
     * @param <T>
     * @return
     */
    public static <T> ResultData<T> success(T t) {
        ResultData<T> r = new ResultData<>();
        r.setResult(1);
        r.setData(t);
        r.setMsg("success");
        return r;
    }

    /**
     * 2 待处理
     *
     * @param
     * @return
     */
    public static <T> ResultData<T> success2(T t) {
        ResultData<T> r = new ResultData<>();
        r.setResult(2);
        r.setData(t);
        r.setMsg("success");
        return r;
    }

    /**
     * 1 执行成功
     *
     * @param t
     * @param msg
     * @param <T>
     * @return
     */
    public static <T> ResultData<T> success3(T t, String msg) {
        ResultData<T> r = new ResultData<>();
        r.setResult(1);
        r.setData(t);
        r.setMsg(msg);
        return r;
    }


    /**
     * 0 失败
     *
     * @return
     */
    public static <T> ResultData<T> error(int code) {
        ResultData<T> r = new ResultData<>();
        r.setResult(code);
        r.setMsg(CodeMsg.getName(code));
        return r;
    }

    /**
     * @param code 状态吗
     * @param t    返回数据
     * @return
     */
    public static <T> ResultData<T> error(int code, T t) {
        ResultData<T> r = new ResultData<>();
        r.setResult(code);
        r.setData(t);
        r.setMsg(CodeMsg.getName(code));
        return r;
    }

    public static <T> ResultData<T> error(CommonException ce) {
        ResultData<T> r = new ResultData<>();
        r.setResult(ce.getCode());
        r.setMsg(ce.getMessage());
        r.setData(null);
        return r;
    }

    public static <T> ResultData<T> error(int code, String msg) {
        ResultData<T> r = new ResultData<>();
        r.setResult(code);
        r.setMsg(msg);
        return r;
    }

    public static <T> ResultData<T> error(int code, String msg, T t) {
        ResultData<T> r = new ResultData<>();
        r.setResult(code);
        r.setMsg(msg);
        r.setData(t);
        return r;
    }

    public static <T> ResultData<T> reply(HashMap replyObj) {
        ResultData<T> r = new ResultData<>();
        r.setResult((Integer) replyObj.get("result"));
        r.setMsg((String) replyObj.get("msg"));
        r.setData((T) replyObj.get("data"));
        return r;
    }

    public static <T> ResultData<T> reply(int code, String msg) {
        ResultData<T> r = new ResultData<>();
        r.setResult(code);
        r.setMsg(msg);
        return r;
    }

}
