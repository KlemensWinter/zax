package com.inovex.zabbixmobile.util;

import android.support.annotation.NonNull;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * Created by felix on 30/11/15.
 */
public class LocalKeyStore {

	private static final String TAG = "LocalKeyStore";
	private static String keyStoreDirectory;

	public static void setKeyStoreDirectory(String keyStoreDirectory) {
		LocalKeyStore.keyStoreDirectory = keyStoreDirectory;
	}

	private static class KeystoreHolder{
		static final LocalKeyStore instance = new LocalKeyStore();
	}

	public static final LocalKeyStore getInstance(){
		return KeystoreHolder.instance;
	}

	private File mKeyStoreFile;
	private KeyStore mKeyStore;


	private LocalKeyStore(){
		File file = new File( keyStoreDirectory + File.separator + "keystore.bks");
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(mKeyStoreFile);
		} catch (FileNotFoundException e) {

		}
		try {
			KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
			store.load(fis,"".toCharArray());
			mKeyStore = store;
			mKeyStoreFile = file;
		} catch (Exception e) {
			mKeyStore = null;
			mKeyStoreFile = null;
		} finally {
			try {
				fis.close();
			} catch (IOException e) {
				// ignore
			}
		}
	}

	public void addCertificate(String host, String port, X509Certificate certificate) throws CertificateException {
		if(mKeyStore != null){
			throw new CertificateException("Certificate can not be added, because key store is not initialized");
		}
		try{
			mKeyStore.setCertificateEntry(getKeyAlias(host, port), certificate);
		} catch (KeyStoreException e) {
			throw new CertificateException("Failed to add certificate to local store",e);
		}
		writeKeystoreToFile();
	}

	@NonNull
	private String getKeyAlias(String host, String port) {
		return host + ":" + port;
	}

	private void writeKeystoreToFile() throws CertificateException {
		OutputStream keyStoreStream = null;
		try{
			keyStoreStream = new FileOutputStream(mKeyStoreFile);
			mKeyStore.store(keyStoreStream, "".toCharArray());
		} catch (FileNotFoundException e) {
			throw new CertificateException("Unable to write key :" + e.getMessage());
		} catch (CertificateException e) {
			throw new CertificateException("Unable to write key :" + e.getMessage());
		} catch (NoSuchAlgorithmException e) {
			throw new CertificateException("Unable to write key :" + e.getMessage());
		} catch (KeyStoreException e) {
			throw new CertificateException("Unable to write key :" + e.getMessage());
		} catch (IOException e) {
			throw new CertificateException("Unable to write key :" + e.getMessage());
		} finally {
			try {
				keyStoreStream.close();
			} catch (IOException e) {
			}
		}
	}

	public boolean checkCertificateIsValid(Certificate certificate, String host, String port){
		if(mKeyStore == null){
			return false;
		}
		Certificate storedCert = null;
		try{
			storedCert = mKeyStore.getCertificate(getKeyAlias(host, port));
			return (storedCert != null && storedCert.equals(certificate));
		} catch (KeyStoreException e) {
			return false;
		}
	}

	public void deleteCertificate(String host, String port){
		if(mKeyStore == null){
			return;
		}
		Certificate storedCert = null;
		try{
			mKeyStore.deleteEntry(getKeyAlias(host, port));
			writeKeystoreToFile();
		} catch (KeyStoreException e) {
			// ignore
		} catch (CertificateException e) {
			Log.e(TAG, "error updating key store file",e);
		}
	}
}
