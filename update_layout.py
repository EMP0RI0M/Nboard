import re

with open("app/src/main/res/layout/keyboard_view.xml", "r") as f:
    content = f.read()

# Replace root LinearLayout with FrameLayout
# First, find the closing tag and replace it
content = re.sub(r'</LinearLayout>\s*$', '</LinearLayout>\n\n    <!-- Floating Transcript Button -->\n    <LinearLayout\n        android:id="@+id/floatingTranscriptButton"\n        android:layout_width="wrap_content"\n        android:layout_height="36dp"\n        android:layout_gravity="top|end"\n        android:layout_marginTop="-40dp"\n        android:layout_marginEnd="8dp"\n        android:background="@drawable/bg_ai_pill"\n        android:elevation="4dp"\n        android:gravity="center_vertical"\n        android:orientation="horizontal"\n        android:paddingStart="12dp"\n        android:paddingEnd="12dp"\n        android:visibility="gone"\n        android:alpha="0"\n        android:clickable="true"\n        android:focusable="true">\n\n        <ImageView\n            android:layout_width="16dp"\n            android:layout_height="16dp"\n            android:src="@drawable/ic_subtitles_lucide"\n            android:tint="@color/ai_text" />\n\n        <TextView\n            android:id="@+id/floatingTranscriptLabel"\n            android:layout_width="wrap_content"\n            android:layout_height="wrap_content"\n            android:layout_marginStart="6dp"\n            android:fontFamily="@font/inter_variable"\n            android:text="Transcript"\n            android:textColor="@color/ai_text"\n            android:textSize="13sp"\n            android:textStyle="bold" />\n    </LinearLayout>\n\n</FrameLayout>\n', content)

# Now replace the opening tag
content = re.sub(r'(<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"\n\s+android:id="@+id/keyboardRoot")', r'<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"\n    android:id="@+id/keyboardFrame"\n    android:layout_width="match_parent"\n    android:layout_height="wrap_content"\n    android:clipChildren="false"\n    android:clipToPadding="false">\n\n\1', content, count=1)

with open("app/src/main/res/layout/keyboard_view.xml", "w") as f:
    f.write(content)
