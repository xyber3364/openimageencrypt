package xyber3364.imagevault

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import kotlinx.android.synthetic.main.activity_password.*


class PasswordActivity : AppCompatActivity(), TextView.OnEditorActionListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_password)

        et_Input2.setOnEditorActionListener(this)
    }

    override fun onBackPressed() {

    }

    override fun onEditorAction(v: TextView?, actionId: Int, event: KeyEvent?): Boolean {
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            val input1 = et_Input1.text.toString()///et_Input1.text.toString()
            val input2 = et_Input2.text.toString()

            if (input1 == input2) {
                if (input2.length > 4) {
                    val intent = Intent()
                    intent.putExtra(INPUT_HASH, Digest.digestTextBase64(input2))

                    setResult(Activity.RESULT_OK, intent)
                    finish()

                } else {
                    tv_Message?.setText(R.string.error_not_long_or_exist)
                }
            } else {
                tv_Message?.setText(R.string.error_not_match)
            }

            /*Log.d("SECURITY",Digest.digestTextBase64(input))
            v?.text = ""

            val ic = ImageCipher(input.toByteArray())
            val encrypt = ic.encrypt(input.toByteArray())
            val decrypt = ic.decrypt(encrypt)

            Log.d("SECURITY",String(decrypt))
            */
            return true
        }

        return false
    }

    companion object {
        const val INPUT_HASH = "INPUT_HASH"
    }
}
