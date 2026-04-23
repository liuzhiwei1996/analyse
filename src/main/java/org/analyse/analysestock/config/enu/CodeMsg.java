package org.analyse.analysestock.config.enu;

/**
 * @author dengzhiqiang
 * @date 2021/11/25
 * @describe
 */
public enum CodeMsg {

    PARAMNOTFOUND("参数不能为空", -600),
    QUERYNULL("查询为空", -601),
    REQUESTPARAMERROR("请求参数错误", -604),
    ACCOUNTPASSWORDERR("账号或密码错误", -1101),
    ABNORMALACCOUNTSTATUS("账号状态异常", -1102),
    ACCOUNTNOTBOUND("账号未绑定手机或邮箱", -1103),
    TOOMANYWRONGPASSWORDS("账号密码错误超过规定次数", -1104),
    ACCOUNTPERMISSIONS("当前账号无此操作权限", -1105),
    ACCOUNTEXIST("该账号已存在！", -1106),
    COMPANYINFOEXIST("该公司已存在！", -1107),
    ACCOUNTISNULLERR("您还未注册，您可联系管理员申请注册，业务联系：（86）0755-26990156。", -1108),
    STOCKISNULLERR("该同业股不存在！", -1109),
    TOKENNOTEXIST("token不存在", -1201),
    TOKENEXPIRED("token过期或不合法", -1202),
    SESSIONEXPIRED("当前会话已经过期", -1301),
    VERIFICATIONCODEEXPIRED("验证码错误或已过期", -1302),
    ILLEGALIP("ip不合法", -701);

    // 成员变量
    private String name;
    private int index;

    // 构造方法
    private CodeMsg(String name, int index) {
        this.name = name;
        this.index = index;
    }

    // 普通方法
    public static String getName(int index) {
        for (CodeMsg c : CodeMsg.values()) {
            if (c.getIndex() == index) {
                return c.name;
            }
        }
        return "未知异常";
    }

    // get set 方法
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

}
