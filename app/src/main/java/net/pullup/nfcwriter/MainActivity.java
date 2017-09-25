package net.pullup.nfcwriter;

import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.Vibrator;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.app.PendingIntent;
import android.content.Intent;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;


public class MainActivity extends AppCompatActivity {
	private String nfcMIME = null;
	private String nfcBody = null;
	private long pattern[] = {0, 5};

	private NfcAdapter nfcAdapter = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		EditText edit = (EditText) findViewById(R.id.editBody);
		edit.setText("text/x-vcard\n" +
				"BEGIN:VCARD\n" +
				"N:ホゲ;ホゲ\n" +
				"TEL;WORK;VOICE:000-000-0000\n" +
				"END:VCARD");

		this.nfcAdapter = NfcAdapter.getDefaultAdapter(this);
	}

	@Override
	protected void onResume() {
		super.onResume();

		PendingIntent pendingIntent = this.createPendingIntent();
		this.nfcAdapter.enableForegroundDispatch(this, pendingIntent, null, null);
	}

	@Override
	protected void onPause() {
		super.onPause();

		this.nfcAdapter.disableForegroundDispatch(this);
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);

		String action = intent.getAction();
		if (TextUtils.isEmpty(action)) {
			return;
		}
		if (!action.equals(NfcAdapter.ACTION_NDEF_DISCOVERED)) {
			Toast.makeText(this, "Error: NDEFフォーマットされていないタグかも", Toast.LENGTH_SHORT).show();
			return;
		}

		if ((this.nfcMIME == null) || (this.nfcBody == null)) {
			return;
		}

		String result = this.writeNFCTag(intent);

		if (result == null) {
			Toast.makeText(this, "書き込み成功", Toast.LENGTH_SHORT).show();

			Vibrator vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
			vib.vibrate(pattern, -1);
		} else {
			Toast.makeText(this, "Error:" + result, Toast.LENGTH_SHORT).show();
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
	}

	private PendingIntent createPendingIntent() {
		Intent intent = new Intent(this, MainActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
		return PendingIntent.getActivity(this, 0, intent, 0);
	}

	public void OnClickSet(View view) {
		this.nfcMIME = null;
		this.nfcBody = null;

		TextView textCondition = (TextView) findViewById(R.id.textCondition);

		EditText edit = (EditText) findViewById(R.id.editBody);
		try {
			String msg = "";
			do {
				String mime;
				String body;

				String line = edit.getText().toString().trim();
				StringBuilder sb = new StringBuilder(line);
				while (sb.length() > 0) {
					char c = sb.charAt(0);
					if (c <= ' ') {
						sb.deleteCharAt(0);
					} else {
						break;
					}
				}

				while (sb.length() > 0) {
					int idx = sb.length() - 1;
					char c = sb.charAt(idx);
					if (c <= ' ') {
						sb.deleteCharAt(idx);
					} else {
						break;
					}
				}

				line = sb.toString();
				String[] lines = line.split("\n", 0);

				if (lines.length < 2) {
					msg = "MIME行や本文が記述されていない";
					break;
				}

				mime = lines[0];
				int pos = mime.indexOf('/');
				if ((mime.length() == 0) || (pos == 0) || ((pos + 1) >= mime.length())) {
					msg = "MIME行が不正";
					break;
				}

				sb.setLength(0);
				for (int i = 1; i < lines.length; i++) {
					if (sb.length() > 0) {
						sb.append("\n");
					}
					sb.append(lines[i]);
				}

				body = sb.toString();
				if (body.length() == 0) {
					msg = "本文が記述されていない";
					break;
				}

				this.nfcMIME = mime;
				this.nfcBody = body;

				msg = "書き込み待機中";
			} while (false);

			textCondition.setText(msg);
		} catch (Exception e) {
			textCondition.setText(e.toString());
		}
	}

	private String writeNFCTag(Intent intent) {
		String result = null;

		Tag tags = (Tag) intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
		if (tags == null) {
			return "タグ情報が取得できない。";
		}

		NdefRecord[] rs = new NdefRecord[]{
				new NdefRecord(NdefRecord.TNF_MIME_MEDIA, this.nfcMIME.getBytes(), new byte[]{}, this.nfcBody.getBytes())
		};

		NdefMessage msg = new NdefMessage(rs);

		Ndef ndef = Ndef.get(tags);
		if (ndef == null) {
			return "NDEFフォーマットできていない。";
		}

		if (!ndef.isWritable()) {
			return "書き込みできないタグ。";
		}

		int messageSize = msg.toByteArray().length;
		if (messageSize > ndef.getMaxSize()) {
			return "メッセージサイズがタグの容量を超えている。";
		}

		try {
			ndef.connect();
			ndef.writeNdefMessage(msg);
		} catch (Exception e) {
			return e.toString();
		} finally {
			try {
				ndef.close();
			} catch (Exception e) {
			}
		}

		return result;
	}
}
