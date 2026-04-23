package org.analyse.analysestock.config.enu;

public enum OperationLogEnum {
    ADD(1),
    DELETE(2),
    UPDATE(3),
    SELECT(4),
    OTHER(5),
    IMPORT(6),
    LOGIN(7);
    private int type;
    OperationLogEnum(int type) {
        this.type = type;
    }

    public int getType() {
        return type;
    }

}
