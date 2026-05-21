package com.familytree.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private WebView webView;
    private ProgressBar progressBar;
    private TextView backBtn;
    private boolean unlocked = false;
    private long lastBackTime = 0;
    private static final String HOME_URL = "https://zhushisanxiangfangfamily.github.io/family-tree/";

    private static final String HASH_HISTORY_JS =
        "(function(){" +
        "var _showPage=showPage,_renderFamilyView=renderFamilyView," +
        "_openMemberDetail=openMemberDetail,_openModal=openModal," +
        "_closeModal=closeModal;" +
        "var _depth=0,_restoring=false;" +
        "function enter(){_depth++;}" +
        "function leave(){_depth--;}" +
        "function top(){return _depth===1;}" +
        "function setHash(h){" +
        "if(!_restoring&&location.hash!==h)location.hash=h;" +
        "}" +
        // Wrap showPage for tab navigation
        "showPage=function(id){" +
        "enter();" +
        "_showPage(id);" +
        "if(top()&&!_restoring)setHash('#'+id);" +
        "leave();" +
        "};" +
        // Wrap renderFamilyView
        "renderFamilyView=function(pid){" +
        "enter();" +
        "_renderFamilyView(pid);" +
        "if(top()&&!_restoring)setHash('#family-'+pid);" +
        "leave();" +
        "};" +
        // Wrap openMemberDetail
        "openMemberDetail=function(id){" +
        "enter();" +
        "_openMemberDetail(id);" +
        "if(top()&&!_restoring)setHash('#member-'+id);" +
        "leave();" +
        "};" +
        // Wrap openModal
        "openModal=function(id){" +
        "enter();" +
        "_openModal(id);" +
        "if(top()&&!_restoring)setHash('#modal-'+id);" +
        "leave();" +
        "};" +
        // Replace closeModal: go back if we pushed history, otherwise just close
        "closeModal=function(id){" +
        "var h=location.hash.replace('#','');" +
        "if(!h||h==='home'||h==='tree'||h==='members'||h==='stories'){" +
        "_closeModal(id);" +
        "}else{history.back();}" +
        "};" +
        // Handle hash change (back/forward)
        "window.addEventListener('hashchange',function(){" +
        "if(_restoring)return;" +
        "_restoring=true;" +
        // Close any open modals
        "document.querySelectorAll('.modal-overlay.open').forEach(function(m){" +
        "m.classList.remove('open');" +
        "});" +
        "var h=location.hash.replace('#','');" +
        "if(h==='home'||h==='tree'||h==='members'||h==='stories'){" +
        "_showPage(h);" +
        "}else if(h.indexOf('family-')===0){" +
        "var pid=parseInt(h.replace('family-',''));" +
        "_showPage('tree');" +
        "_renderFamilyView(pid);" +
        "}else if(h.indexOf('member-')===0){" +
        "var mid=parseInt(h.replace('member-',''));" +
        "_showPage('tree');" +
        "_openMemberDetail(mid);" +
        "}else if(h.indexOf('modal-')===0){" +
        "_openModal(h.replace('modal-',''));" +
        "}" +
        "setTimeout(function(){_restoring=false;},100);" +
        "});" +
        // Set initial hash
        "var el=document.querySelector('.page.active');" +
        "if(el&&el.id&&!location.hash){" +
        "history.replaceState(null,'','#'+el.id.replace('page-',''));" +
        "}" +
        "})();";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        float density = getResources().getDisplayMetrics().density;

        RelativeLayout layout = new RelativeLayout(this);
        layout.setLayoutParams(new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT));

        webView = new WebView(this);
        RelativeLayout.LayoutParams wvParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT);
        layout.addView(webView, wvParams);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        progressBar.setVisibility(View.GONE);
        RelativeLayout.LayoutParams pbParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT, 6);
        pbParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        layout.addView(progressBar, pbParams);

        // Circular back button, positioned above the site's bottom tab bar
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(0xCC1A1A1A);

        backBtn = new TextView(this);
        backBtn.setText("←");
        backBtn.setTextColor(Color.WHITE);
        backBtn.setTextSize(20);
        backBtn.setGravity(Gravity.CENTER);
        backBtn.setBackground(circle);
        backBtn.setVisibility(View.GONE);
        int btnSize = (int)(48 * density);
        RelativeLayout.LayoutParams btnParams = new RelativeLayout.LayoutParams(btnSize, btnSize);
        btnParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        btnParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        btnParams.setMargins(0, 0, (int)(16 * density), (int)(72 * density));
        layout.addView(backBtn, btnParams);
        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (webView.canGoBack()) webView.goBack();
            }
        });

        setContentView(layout);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setUserAgentString(settings.getUserAgentString() + " FamilyTreeApp/1.0");

        webView.clearCache(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                String errorHtml = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head>"
                    + "<body style='background:#F5F0E8;display:flex;align-items:center;justify-content:center;height:100vh;margin:0;font-family:system-ui,sans-serif;text-align:center'>"
                    + "<div><div style='font-size:48px;margin-bottom:16px'>&#128225;</div>"
                    + "<h2 style='color:#4a3000'>网络连接失败</h2>"
                    + "<p style='color:#8B7355'>请检查网络后重新打开</p></div></body></html>";
                view.loadDataWithBaseURL(null, errorHtml, "text/html", "UTF-8", null);
            }

            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                backBtn.setVisibility(view.canGoBack() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (!unlocked) {
                    unlocked = true;
                    view.evaluateJavascript(
                        "sessionStorage.setItem('ft_unlocked','1');location.reload();", null);
                } else {
                    view.evaluateJavascript(HASH_HISTORY_JS, null);
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int progress) {
                if (progress < 100) {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(progress);
                } else {
                    progressBar.setVisibility(View.GONE);
                }
            }
        });

        webView.loadUrl(HOME_URL);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            long now = System.currentTimeMillis();
            if (now - lastBackTime < 2000) {
                super.onBackPressed();
            } else {
                lastBackTime = now;
                Toast.makeText(this, "再按一次退出", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (webView.canGoBack()) {
                webView.goBack();
                return true;
            } else {
                long now = System.currentTimeMillis();
                if (now - lastBackTime < 2000) {
                    return super.onKeyDown(keyCode, event);
                } else {
                    lastBackTime = now;
                    Toast.makeText(this, "再按一次退出", Toast.LENGTH_SHORT).show();
                    return true;
                }
            }
        }
        return super.onKeyDown(keyCode, event);
    }
}
