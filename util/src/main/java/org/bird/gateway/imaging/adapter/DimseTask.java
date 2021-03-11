package org.bird.gateway.imaging.adapter;

import lombok.extern.slf4j.Slf4j;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.CancelRQHandler;
import org.dcm4che3.net.pdu.PresentationContext;


/**
 * @date 9:35 2021-3-1
 * @description TODO
 */
@Slf4j
public abstract class DimseTask implements Runnable, CancelRQHandler {


    protected final Association as;
    protected final PresentationContext pc;
    protected final Attributes cmd;


    protected volatile boolean canceled;
    protected volatile Thread runThread;

    public DimseTask(Association as, PresentationContext pc, Attributes cmd) {
        this.as = as;
        this.pc = pc;
        this.cmd = cmd;

        int msgId = cmd.getInt(Tag.MessageID, -1);
        as.addCancelRQHandler(msgId, this);
    }

    @Override
    public void onCancelRQ(Association as) {
        log.info(this.getClass().getSimpleName() + " onCancelRQ");

        if (!canceled) {
            canceled = true;
            synchronized (this) {
                if (runThread != null) {
                    /**
                     * Note that interrupt does not kill the thread and instead leads to InterruptedException
                     * being thrown by most long duration methods (if used in subclasses).
                     * Subclasses need to make sure to set runThread, provide response even if interrupted
                     * (catch clause) and cleanup cancelRQHandler (finally clause)
                     */
                    runThread.interrupt();
                }
            }
        }
    }



}
