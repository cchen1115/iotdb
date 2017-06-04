package cn.edu.thu.tsfiledb.qp.physical.plan;

import java.util.ArrayList;
import java.util.List;

import cn.edu.thu.tsfile.common.exception.ProcessorException;
import cn.edu.thu.tsfile.timeseries.read.qp.Path;
import cn.edu.thu.tsfiledb.qp.exec.QueryProcessExecutor;
import cn.edu.thu.tsfiledb.qp.logical.operator.Operator.OperatorType;


/**
 * given a update plan and construct a {@code UpdatePlan}
 * 
 * @author kangrong
 *
 */
public class UpdatePlan extends PhysicalPlan {
    private long startTime;
    private long endTime;
    private String value;
    private Path path;

    public UpdatePlan() {
        super(false, OperatorType.UPDATE);
    }

    public UpdatePlan(long startTime, long endTime, String value, Path path) {
        super(false, OperatorType.UPDATE);
        this.setStartTime(startTime);
        this.setEndTime(endTime);
        this.setValue(value);
        this.setPath(path);
    }

    @Override
    public boolean processNonQuery(QueryProcessExecutor exec) throws ProcessorException {
        return exec.update(path, startTime, endTime, value);
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Path getPath() {
        return path;
    }

    public void setPath(Path path) {
        this.path = path;
    }
    
    @Override
    public List<Path> getInvolvedSeriesPaths() {
        List<Path> ret = new ArrayList<Path>();
        if (path != null)
            ret.add(path);
        return ret;
    }
}
