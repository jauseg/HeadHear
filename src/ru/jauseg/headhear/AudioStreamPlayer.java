package ru.jauseg.headhear;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.SystemClock;
import android.util.Log;

public class AudioStreamPlayer
{
	protected static final String TAG = "AudioStreamPlayer";
	private Thread playerThread;
	private boolean isPlay = false;
	private boolean isNeedNotify = false;

	private short buffers[][];
	private int playIndex;
	private int addedIndex;

	private AudioTrack audioTrack;
	private int sampleRateInHz;

	private Object locker = new Object();

	private void startPlayThread()
	{
		if (!isPlay)
		{
			playerThread = new Thread()
			{
				@Override
				public void run()
				{
					audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRateInHz,
							AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, AudioConfig.BUFFER_SIZE,
							AudioTrack.MODE_STREAM);

					audioTrack.play();

					while (!isInterrupted())
					{
						short[] buffer;

						long timeWait = SystemClock.elapsedRealtime();

						synchronized (locker)
						{
							if (playIndex == addedIndex)
							{
								try
								{
									isNeedNotify = true;
									locker.wait();
								}
								catch (InterruptedException e)
								{
									break;
								}
							}

							buffer = buffers[playIndex];
						}

						timeWait = SystemClock.elapsedRealtime() - timeWait;

						long timePlay = SystemClock.elapsedRealtime();
						audioTrack.write(buffer, 0, buffer.length);
						timePlay = SystemClock.elapsedRealtime() - timePlay;
						//long delta = (long) (timePlay + timeWait - ((double) AudioConfig.BUFFER_SIZE * 1000.0 / (double) AudioConfig.SAMPLE_RATE_IN_HZ));

						//Log.v(TAG, "total delta = " + delta + " timePlay = " + timePlay + " time wait = " + timeWait);

						synchronized (locker)
						{
							playIndex = (playIndex + 1) % buffers.length;
						}
					}

					audioTrack.stop();
					isPlay = false;
				}
			};

			playerThread.start();
			isPlay = true;
		}
	}

	private void init(int buffersCount)
	{
		playIndex = 0;
		addedIndex = 0;
		buffers = new short[buffersCount][];
	}

	public AudioStreamPlayer(int buffersCount, int sampleRateInHz)
	{
		this.sampleRateInHz = sampleRateInHz;
		init(buffersCount);
	}

	public AudioStreamPlayer()
	{
		this(AudioConfig.BUFFERS_COUNT, AudioConfig.SAMPLE_RATE_IN_HZ);
	}

	public void play(short[] buffer)
	{
		if (!isPlay)
		{
			//SystemClock.sleep(500);
			startPlayThread();
		}

		synchronized (locker)
		{
			buffers[addedIndex] = buffer;

			addedIndex = (addedIndex + 1) % buffers.length;

			if (isNeedNotify)
			{
				isNeedNotify = false;
				locker.notifyAll();
			}
		}

	}

	public void stop()
	{
		if (isPlay)
		{
			if (playerThread != null && playerThread.isAlive() && !playerThread.isInterrupted())
			{
				playerThread.interrupt();
			}
			isPlay = false;
		}
	}
}
