package org.analyse.analysestock.config;

/**
 * @author dengzhiqiang
 * @date 2021/11/25
 * @describe
 */
public class ResultData<T> {
    /**
     * 0失败 1成功
     */
    private Integer result;

    /**
     * 附加描述
     */
    private String msg;

    /**
     * 返回数据
     */
    private T data;


    public Integer getResult() {
        return result;
    }

    public void setResult(Integer result) {
        this.result = result;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    @Override
    public String toString() {
        return "ResultData [result=" + result + ", data=" + data + ", msg=" + msg + "]";
    }

}
