package com.example.marvelmind_track;

import android.util.Log;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Created by ideog on 13.01.2018.
 */

class Marvelmind {
    public static final int MM_FRAME_HEDGE_POS_32BIT = 0x0011;
    public static final int MM_FRAME_HEDGE_POS_32BIT_NEWTIMESTAMPS = 0x0081;
    public static final int MM_FRAME_IMU_FUSION= 0x0005;
    public static final int MM_FRAME_HEDGE_POS_16BIT_SHORT = 0x2001;
    public static final int MM_FRAME_ANGLE_YAW_SHORT = 0x2005;

    public MarvelmindPos last_linear_values = new MarvelmindPos();
    public MarvelmindPos old_last_linear_values = new MarvelmindPos();

    public int isButtonPressed = 0;
    //public int left_hedge_addr = 19;
    //public int right_hedge_addr = 20;

    private int cycle_buffer_end = 0;
    private int cycle_buffer_start = 0;
    private static final int CYCLE_BUFSIZE = 8192;
    private static final int US_PDGSIZE = 29;
    private static final int US_NEWTIMESTAMPS_PDGSIZE = 33;
    private static final int IMU_FUSION_PDGSIZE = 49;
    private static final int US_POS_SHORT_PDGSIZE = 12;
    private static final int IMU_ANGLE_YAW_PDGSIZE = 9;
    private short[] usPositionDatagram = new short[US_PDGSIZE+30];
    private short[] imuFusionDatagram = new short[IMU_FUSION_PDGSIZE];
    private short[] usPositionShortDatagram = new short[US_POS_SHORT_PDGSIZE];
    private short[] angleYawDatagram = new short[IMU_ANGLE_YAW_PDGSIZE];
    private byte cycle_buffer[] = new byte[CYCLE_BUFSIZE];

    private ReadWriteLock resLock;
    private Lock resRLock;

    public Marvelmind() {
        resLock= new ReentrantReadWriteLock();
        resRLock= resLock.readLock();
    }

    public int modRTU_CRC(short[] buf, int len) {
        int crc = 0xffff;
        for (int ind = 0; ind < len; ind++) {
            crc ^= (int) buf[ind] & 0xff;
            for (int i = 8; i != 0; i--) {
                if ((crc & 0x0001) != 0) {
                    crc >>= 1;
                    crc ^= 0xA001;
                } else crc >>= 1;
            }
        }
        return crc;
    }

    private boolean isUsPositionHeader(int ofs) {
        return ((cycle_buffer[cycle_ofs(ofs-3)] == (byte) (0xff)) &&
                (cycle_buffer[cycle_ofs(ofs-2)] == (byte) (0x47)) &&
                (cycle_buffer[cycle_ofs(ofs-1)] == (byte) (0x11)) &&
                (cycle_buffer[cycle_ofs(ofs)] == (byte) (0x00)));
    }

    private boolean isUsPositionNewTimeStampsHeader(int ofs) {
        return ((cycle_buffer[cycle_ofs(ofs-3)] == (byte) (0xff)) &&
                (cycle_buffer[cycle_ofs(ofs-2)] == (byte) (0x47)) &&
                (cycle_buffer[cycle_ofs(ofs-1)] == (byte) (0x81)) &&
                (cycle_buffer[cycle_ofs(ofs)] == (byte) (0x00)));
    }

    private boolean isImuFusionHeader(int ofs) {
        return ((cycle_buffer[cycle_ofs(ofs-3)] == (byte) (0xff)) &&
                (cycle_buffer[cycle_ofs(ofs-2)] == (byte) (0x47)) &&
                (cycle_buffer[cycle_ofs(ofs-1)] == (byte) (0x05)) &&
                (cycle_buffer[cycle_ofs(ofs)] == (byte) (0x00)));
    }

    private boolean isShortUsPositionHeader(int ofs) {
        return ((cycle_buffer[cycle_ofs(ofs-3)] == (byte) (0xff)) &&
                (cycle_buffer[cycle_ofs(ofs-2)] == (byte) (0x47)) &&
                (cycle_buffer[cycle_ofs(ofs-1)] == (byte) (0x01)) &&
                (cycle_buffer[cycle_ofs(ofs)] == (byte) (0x20)));
    }

    private boolean isAngleYawHeader(int ofs) {
        return ((cycle_buffer[cycle_ofs(ofs-3)] == (byte) (0xff)) &&
                (cycle_buffer[cycle_ofs(ofs-2)] == (byte) (0x47)) &&
                (cycle_buffer[cycle_ofs(ofs-1)] == (byte) (0x05)) &&
                (cycle_buffer[cycle_ofs(ofs)] == (byte) (0x20)));
    }

    public boolean tryReadFrame(int j, int frameSize, short[] buf) {
        int pdgEnd = 0;
        int cbs = cycle_ofs(j - 3);
        while (true) {
            pdgEnd += 1;
            if (pdgEnd == frameSize) {
                for (int l = 0; l < pdgEnd; l++) {
                    buf[l] = (short) (cycle_buffer[cycle_ofs(l+cbs)] & 0xff);
                }
                cycle_buffer_start= cycle_ofs(pdgEnd+cbs);
                return true;
            }

            if (cycle_ofs(pdgEnd+cbs) == cycle_buffer_end)
                break;
        }
        return false;
    }

    public int cycle_ofs(int ofs) {
        if (ofs<0)
            return ofs + CYCLE_BUFSIZE;
        return (ofs % CYCLE_BUFSIZE);
    }

    public boolean addReceivedData(byte[] data) {
        if (data != null && data.length > 0) {
            int numBytesRead = data.length;
            for (int k = 0; k < numBytesRead; k++) {
                cycle_buffer[cycle_ofs(k + cycle_buffer_end)] = data[k];
            }

            cycle_buffer_end = cycle_ofs(cycle_buffer_end + numBytesRead);

            return true;
        }

        return false;
    }

    public int findFrame() {
            int j = cycle_ofs(cycle_buffer_start);
            int jofs;
            while (j != cycle_buffer_end) {
                jofs= cycle_ofs(j-3);

                if (isUsPositionHeader(jofs)) {
                    if (tryReadFrame(jofs,US_PDGSIZE,usPositionDatagram)) {
                        int parseRes= parseUSframe(usPositionDatagram, false);
                        if (parseRes == 0) {
                            return MM_FRAME_HEDGE_POS_32BIT;
                        } else {
                            return parseRes;
                        }
                    }
                }
                else if (isUsPositionNewTimeStampsHeader(jofs)) {
                    if (tryReadFrame(jofs,US_NEWTIMESTAMPS_PDGSIZE,usPositionDatagram)) {
                        int parseRes= parseUSframe(usPositionDatagram, true);
                        if (parseRes == 0) {
                            return MM_FRAME_HEDGE_POS_32BIT_NEWTIMESTAMPS;
                        } else {
                            return parseRes;
                        }
                    }
                }
                else if (isImuFusionHeader(jofs)) {
                    if (tryReadFrame(jofs,IMU_FUSION_PDGSIZE,imuFusionDatagram)) {
                        int parseRes= parseIMUFusionframe(imuFusionDatagram);
                        if (parseRes == 0) {
                            return MM_FRAME_IMU_FUSION;
                        } else {
                            return -2;
                        }
                    }
                }
                else if (isShortUsPositionHeader(j)) {
                    if (tryReadFrame(j,US_POS_SHORT_PDGSIZE,usPositionShortDatagram)) {
                        int parseRes= parseShortUSframe(usPositionShortDatagram);
                        if (parseRes == 0) {
                            return MM_FRAME_HEDGE_POS_16BIT_SHORT;
                        } else {
                            return -2;
                        }
                    }
                }
                else if (isAngleYawHeader(jofs)) {
                    if (tryReadFrame(jofs,IMU_ANGLE_YAW_PDGSIZE,angleYawDatagram)) {
                        int parseRes= parseAngleYawframe(angleYawDatagram);
                        if (parseRes == 0) {
                            return MM_FRAME_ANGLE_YAW_SHORT;
                        } else {
                            return -2;
                        }
                    }
                }

                j= cycle_ofs(j+1);
            }//for j
        cycle_buffer_start= cycle_buffer_end;
        return -1;
    }

    private int parseUSframe(short[] frame, boolean newTimestamps) {
        int ofs= 0;
        int size= US_PDGSIZE;
        if (newTimestamps) {
          ofs= 4;
          size+= 4;
        }
        int crc = frame[27+ofs] | (frame[28+ofs] << 8);
        int timestamp = frame[5] |
                (frame[6] << 8) |
                (frame[7] << 16) |
                (frame[8] << 24);
        int address = frame[ofs+22];
        int vx = frame[ofs+9] |
                (frame[ofs+10] << 8) |
                (frame[ofs+11] << 16) |
                (frame[ofs+12] << 24);
        int vy = frame[ofs+13] |
                (frame[ofs+14] << 8) |
                (frame[ofs+15] << 16) |
                (frame[ofs+16] << 24);
        int vz = frame[ofs+17] |
                (frame[ofs+18] << 8) |
                (frame[ofs+19] << 16) |
                (frame[ofs+20] << 24);
        int flags= frame[ofs+21];
        int button = ((flags << 2) & 0xff) >> 7;

        int pair_v = frame[ofs+23] |
                    (frame[ofs+24] << 8);

        if (modRTU_CRC(frame, size - 2) != crc) {
            return -9;
        }
        if (timestamp == last_linear_values.timestamp) return -10;
        //if ((address != left_hedge_addr) && (address != right_hedge_addr)) return -1;

        resRLock.lock();
        try {
            old_last_linear_values.set(last_linear_values);
            last_linear_values.timestamp = timestamp;
            last_linear_values.address = address;
            last_linear_values.pos.set(vx, vy, vz);
            //last_linear_values.oriented = ((pair_v & 0x1000) != 0);
            //last_linear_values.angle = (pair_v & 0xfff) / 10.0f;
            isButtonPressed = button;
        }
        finally {
            resRLock.unlock();
        }
        return 0;
    }// parseUSFrame

    private int parseShortUSframe(short[] frame) {
        int crc = frame[10] | (frame[11] << 8);

        int vx_cm = getInt16Value(frame, 5);
        int vy_cm = getInt16Value(frame, 7);

        if (modRTU_CRC(frame, US_POS_SHORT_PDGSIZE - 2) != crc) {
            return -1;
        }
        //if (timestamp == last_linear_values.timestamp) return -1;
        //if ((address != left_hedge_addr) && (address != right_hedge_addr)) return -1;

        resRLock.lock();
        try {
            old_last_linear_values.set(last_linear_values);
            last_linear_values.timestamp = 0;
            last_linear_values.address = 0;
            last_linear_values.pos.set(vx_cm*10, vy_cm*10, 0);
            //last_linear_values.oriented = ((pair_v & 0x1000) != 0);
            //last_linear_values.angle = (pair_v & 0xfff) / 10.0f;
            isButtonPressed = 0;
        }
        finally {
            resRLock.unlock();
        }
        return 0;
    }// parseShortUSframe

    private int getInt16Value(short[] frame, int ofs) {
        int res= frame[ofs] | (frame[ofs+1] <<8);
        if ((res&0x8000)!=0) {
            res|= 0xffff0000;
        }

        return res;
    }

    private double atan2_m(double y, double x) {
        if (x>0.0) {
            return Math.atan(y/x);
        }

        if (x<0.0) {
            if (y>=0.0) return Math.atan(y/x)+Math.PI;
            else if (y<0.0) return Math.atan(y/x)-Math.PI;
            else return 0.0;
        }

        if (y>=0.0) {
            return Math.PI/2.0;
        }
        if (y<0.0) {
            return -Math.PI/2.0;
        }

        return 0.0;
    }

    private int parseIMUFusionframe(short[] frame) {
        int crc = frame[47] | (frame[48] << 8);

        float q0 = getInt16Value(frame, 5+12)/10000.0f;
        float q1 = getInt16Value(frame, 5+14)/10000.0f;
        float q2 = getInt16Value(frame, 5+16)/10000.0f;
        float q3 = getInt16Value(frame, 5+18)/10000.0f;

        if (modRTU_CRC(frame, IMU_FUSION_PDGSIZE - 2) != crc) {
            return -1;
        }

        resRLock.lock();
        try {
            double ang= (atan2_m(2*(q0*q3+q1*q2),1-2*(q2*q2+q3*q3)));
            last_linear_values.oriented= true;
            last_linear_values.angle= (float) (ang*(180.0/Math.PI));
        }
        finally {
            resRLock.unlock();
        }
        return 0;
    }// parseIMUFusionframe

    private int parseAngleYawframe(short[] frame) {
        int crc = frame[7] | (frame[8] << 8);

        if (modRTU_CRC(frame, IMU_ANGLE_YAW_PDGSIZE - 2) != crc) {
            return -1;
        }

        float ang = getInt16Value(frame, 5+0)/10.0f;

        resRLock.lock();
        try {
            last_linear_values.oriented= true;
            last_linear_values.angle= (float) (ang);
        }
        finally {
            resRLock.unlock();
        }
        return 0;
    }// parseAngleYawframe
}
