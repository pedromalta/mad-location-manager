package com.example.lezh1k.sensordatacollector;

import android.annotation.SuppressLint;
import android.hardware.GeomagneticField;
import android.hardware.SensorManager;
import android.opengl.*;

/**
 * Created by lezh1k on 12/14/17.
 */

public class FilterGMA {
    public static int east = 0;
    public static int north = 1;
    public static int up = 2;

    private GPSAccKalmanFilter kfLat;
    private GPSAccKalmanFilter kfLon;
    private GPSAccKalmanFilter kfAlt;

    float[] R = new float[16];
    float[] RI = new float[16]; //inverse
    float[] I = new float[16]; //inclinations

    float[] gravity = new float[4];
    float[] geomagnetic = new float[4];
    float[] gyroscope = new float[4];
    float[] linAcc = new float[4];

    float[] accAxis = new float[4];
    float[] velAxis = new float[4];

    float declination = 0.0f;

    double gpsLat = 0.0;
    double gpsLon = 0.0;
    double gpsAlt = 0.0;
    double gpsHorizontalDop = 0.0;
    double gpsVerticalDop = 0.0;
    double gpsSpeed;
    double gpsCourse;

    double filteredLat = 0.0;
    double filteredLon = 0.0;
    double filteredAlt = 0.0;
    float[] filteredVel = new float[3];

    private long timeStamp;

    public FilterGMA() {
    }

    public void init(double[] linAccDeviations) {
        if (kfLon != null || kfLat != null || kfAlt != null)
            return; //already initalized
        if (gpsAlt == 0.0 && gpsLat == 0.0 && gpsLon == 0.0)
            return;

        timeStamp = System.currentTimeMillis();
        kfLon = new GPSAccKalmanFilter(Coordinates.LongitudeToMeters(gpsLon),
                velAxis[east],
                2.0,
                linAccDeviations[east],
                timeStamp);
        kfLat = new GPSAccKalmanFilter(Coordinates.LatitudeToMeters(gpsLat),
                velAxis[north],
                2.0,
                linAccDeviations[north],
                timeStamp);
        kfAlt = new GPSAccKalmanFilter(gpsAlt,
                velAxis[up],
                3.518522417151836,
                linAccDeviations[up],
                timeStamp);
    }

    private MadgwickAHRS m_ahrs = new MadgwickAHRS(50, 0.1f);

    private void rebuildR(DeviationCalculator dc) {
//        //todo use Madgwick here later
//        if (!SensorManager.getRotationMatrix(R, I, gravity, geomagnetic)) {
//            //todo log
//            return;
//        }
//        android.opengl.Matrix.invertM(RI, 0, R, 0);

        m_ahrs.MadgwickAHRSupdate(gyroscope[0], gyroscope[1], gyroscope[2],
                gravity[0], gravity[1], gravity[2],
                geomagnetic[0], geomagnetic[1], geomagnetic[2]);
        R = m_ahrs.getRotationMatrix();
        android.opengl.Matrix.invertM(RI, 0, R, 0);

    }

    private void rebuildAccAxis() {
        android.opengl.Matrix.multiplyMV(accAxis, 0, RI, 0, this.linAcc, 0);
    }

    public void setGyroscope(float[] gyroscope, DeviationCalculator dc) {
        if (dc.getFrequencyMean() == 0.0) return;
        System.arraycopy(gyroscope, 0, this.gyroscope, 0, 3);
        rebuildR(dc);
    }

    public void setGravity(float[] gravity, DeviationCalculator dc) {
        if (dc.getFrequencyMean() == 0.0) return;
        System.arraycopy(gravity, 0, this.gravity, 0, 3);
        rebuildR(dc);
    }

    public void setGeomagnetic(float[] geomagnetic, DeviationCalculator dc) {
        if (dc.getFrequencyMean() == 0.0) return;
        System.arraycopy(geomagnetic, 0, this.geomagnetic, 0, 3);
        rebuildR(dc);
    }

    public void setGpsHorizontalDop(double gpsHorizontalDop) {
        this.gpsHorizontalDop = gpsHorizontalDop;
    }

    public void setGpsVerticalDop(double gpsVerticalDop) {
        this.gpsVerticalDop = gpsVerticalDop;
    }

    private void refreshFilteredValues(double predictedPosLon,
                                       double predictedVelLon,
                                       double predictedPosLat,
                                       double predictedVelLat,
                                       double predictedPosAlt,
                                       double predictedVelAlt) {
        GeoPoint predictedPoint = Coordinates.MetersToGeoPoint(predictedPosLon,
                predictedPosLat);

        double predictedVE, predictedVN;
        predictedVE = predictedVelLon;
        predictedVN = predictedVelLat;
//        double resultantV = Math.sqrt(Math.pow(predictedVE, 2.0) + Math.pow(predictedVN, 2.0));

        filteredLat = predictedPoint.Latitude;
        filteredLon = predictedPoint.Longitude;
        filteredAlt = predictedPosAlt;

//        filteredSpeed = resultantV;
        filteredVel[east] = (float) predictedVE;
        filteredVel[north] = (float) predictedVN;
        filteredVel[up] = (float) predictedVelAlt;
    }

    public void setLinAcc(float[] linAcc) {
        Commons.LowPassFilterArr(0.1f, this.linAcc, linAcc);
        double predictedPosLat, predictedPosLon, predictedPosAlt;
        double predictedVelLat, predictedVelLon, predictedVelAlt;

        if (kfLat == null || kfLon == null || kfAlt == null) {
            return; //wait for initialization
        }

        timeStamp = System.currentTimeMillis();
        rebuildAccAxis();
        kfLon.Predict(timeStamp, accAxis[east]);
        kfLat.Predict(timeStamp, accAxis[north]);
        kfAlt.Predict(timeStamp, accAxis[up]);

        predictedPosLat = kfLat.getPredictedPosition();
        predictedVelLat = kfLat.getPredictedVelocity();

        predictedPosLon = kfLon.getPredictedPosition();
        predictedVelLon = kfLon.getPredictedVelocity();

        predictedPosAlt = kfAlt.getPredictedPosition();
        predictedVelAlt = kfAlt.getPredictedVelocity();

        refreshFilteredValues(predictedPosLon, predictedVelLon, predictedPosLat, predictedVelLat,
                predictedPosAlt, predictedVelAlt);
    }

    public void setGpsPosition(double gpsLat, double gpsLon, double gpsAlt) {
        this.gpsLat = gpsLat;
        this.gpsLon = gpsLon;
        this.gpsAlt = gpsAlt;

        GeomagneticField gf = new GeomagneticField((float)gpsLat, (float)gpsLon, (float)gpsAlt, System.currentTimeMillis());
        declination = gf.getDeclination();

        if (kfLat == null || kfLon == null || kfAlt == null) {
            return; //wait for initialization
        }

        kfLon.Update(Coordinates.LongitudeToMeters(gpsLon),
                velAxis[east],
                gpsHorizontalDop * 0.01,
                0.0);
        kfLat.Update(Coordinates.LatitudeToMeters(gpsLat),
                velAxis[north],
                gpsHorizontalDop * 0.01,
                0.0);
        kfAlt.Update(gpsAlt,
                -velAxis[up],
                gpsVerticalDop,
                0.0);

        double predictedPosLat = kfLat.getCurrentPosition();
        double predictedVelLat = kfLat.getCurrentVelocity();

        double predictedPosLon = kfLon.getCurrentPosition();
        double predictedVelLon = kfLon.getCurrentVelocity();

        double predictedPosAlt = kfAlt.getCurrentPosition();
        double predictedVelAlt = kfAlt.getCurrentVelocity();

//        refreshFilteredValues(predictedPosLon, predictedVelLon, predictedPosLat, predictedVelLat,
//                predictedPosAlt, predictedVelAlt);
    }

    public void setGpsSpeed(double gpsSpeed) {
        this.gpsSpeed = gpsSpeed;
    }

    public void setGpsCourse(double gpsCourse) {
        this.gpsCourse = gpsCourse;
    }

    public float[] getAccAxis() {
        return accAxis;
    }

    public float[] getVelAxis() {
        return velAxis;
    }

    public double getFilteredLat() {
        return filteredLat;
    }

    public double getFilteredLon() {
        return filteredLon;
    }

    public double getFilteredAlt() {
        return filteredAlt;
    }

    public float[] getFilteredVel() {
        return filteredVel;
    }

    @SuppressLint("DefaultLocale")
    public String debugString() {
        return String.format("" +
                        "declination:%f\n" +
                        "Lat:%f\nLon:%f\nAlt:%f\n" +
                        "Acc: E%.2f,N=%.2f,U=%.2f\n" +
                        "LAcc: E%.2f,N=%.2f,U=%.2f\n" +
                        "Vel: E=%.2f,N=%.2f,U:%.2f\n" +
                        "FLat:%f\nFLon:%f\nFAlt:%f\n" +
                        "FSE:%f\nFSN:%f\nFSU:%f\n" +
                        "Distance: %f",
                declination,
                gpsLat, gpsLon, gpsAlt,
                accAxis[east], accAxis[north], accAxis[up],
                linAcc[0], linAcc[1], linAcc[2],
                velAxis[east], velAxis[north], velAxis[up],
                filteredLat, filteredLon, filteredAlt,
                filteredVel[east], filteredVel[north], filteredVel[up],
                Coordinates.geoDistanceMeters(gpsLon, gpsLat, filteredLon, filteredLat));
    }


}
