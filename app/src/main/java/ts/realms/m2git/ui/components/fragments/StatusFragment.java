package ts.realms.m2git.ui.components.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import ts.realms.m2git.R;
import ts.realms.m2git.core.command.tasks.local.StatusTask;
import ts.realms.m2git.core.models.Repo;
import ts.realms.m2git.ui.screens.commitDiff.CommitDiffActivity;
import ts.realms.m2git.ui.screens.main.BaseCompatActivity.OnBackClickListener;
import ts.realms.m2git.ui.screens.repoDetail.RepoDetailFragment;

/**
 * Created by sheimi on 8/5/13.
 */
public class StatusFragment extends RepoDetailFragment {

    private static final int SWIPE_MIN_DISTANCE = 200; // 最小滑动距离
    private static final int SWIPE_MIN_VELOCITY = 200; // 最小滑动速度
    private Repo mRepo;
    private ProgressBar mLoadding;
    private TextView mStatus;
    private Button mUnstagedDiff;
    private Button mStagedDiff;
    private GestureDetector gestureDetector;

    public static StatusFragment newInstance(Repo mRepo) {
        StatusFragment fragment = new StatusFragment();
        Bundle bundle = new Bundle();
        bundle.putSerializable(Repo.TAG, mRepo);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_status, container, false);
        getRawActivity().setStatusFragment(this);

        Bundle bundle = getArguments();
        mRepo = (Repo) bundle.getSerializable(Repo.TAG);
        if (mRepo == null && savedInstanceState != null) {
            mRepo = (Repo) savedInstanceState.getSerializable(Repo.TAG);
        }
        if (mRepo == null) {
            return v;
        }
        mLoadding = v.findViewById(R.id.loading);
        mStatus = v.findViewById(R.id.status);
        mStatus.setMovementMethod(ScrollingMovementMethod.getInstance());
        mStagedDiff = v.findViewById(R.id.button_staged_diff);
        mUnstagedDiff = v.findViewById(R.id.button_unstaged_diff);
        mStagedDiff.setOnClickListener(v1 -> showDiff("HEAD", "dircache"));
        mUnstagedDiff.setOnClickListener(v2 -> showDiff("dircache", "filetree"));
        // 初始化手势检测
        initGestureDetector(v);
        reset();
        return v;
    }

    /**
     * 初始化手势检测器
     */
    private void initGestureDetector(View rootView) {
        // 创建手势监听器
        GestureDetector.SimpleOnGestureListener listener = new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2,
                                   float velocityX, float velocityY) {
                // 计算垂直滑动距离
                float dy = e2.getY() - e1.getY();

                // 判断是否为下滑刷新手势
                // dy > 0 表示向下滑动
                if (dy > 0 &&
                    Math.abs(dy) > SWIPE_MIN_DISTANCE &&
                    Math.abs(velocityY) > SWIPE_MIN_VELOCITY) {

                    // 检查TextView是否在顶部，避免与滚动冲突
                    if (mStatus != null && mStatus.getScrollY() == 0) {
                        // 执行下滑刷新
                        performSwipeRefresh();
                        return true; // 消费事件
                    }
                }
                return false;
            }

            @Override
            public boolean onDown(MotionEvent e) {
                // 必须返回true，否则onFling不会触发
                return true;
            }
        };

        // 初始化手势检测器
        gestureDetector = new GestureDetector(requireContext(), listener);

        // 设置根布局的触摸监听
        rootView.setOnTouchListener((v, event) -> {
            // 将触摸事件传递给手势检测器
            boolean handled = gestureDetector.onTouchEvent(event);
            // 保持可访问性
            v.performClick();
            // 如果手势检测器处理了事件，返回true，否则返回false让其他视图处理
            return handled;
        });
    }

    /**
     * 执行下滑刷新操作
     */
    private void performSwipeRefresh() {
        // 检查是否正在加载，避免重复刷新
        if (mLoadding != null && mLoadding.getVisibility() != View.VISIBLE) {
            // 调用reset方法刷新状态
            reset();
        }
    }

    private void showDiff(String oldCommit, String newCommit) {
        Intent intent = new Intent(getRawActivity(),
            CommitDiffActivity.class);
        intent.putExtra(CommitDiffActivity.OLD_COMMIT, oldCommit);
        intent.putExtra(CommitDiffActivity.NEW_COMMIT, newCommit);
        intent.putExtra(CommitDiffActivity.SHOW_DESCRIPTION, false);
        intent.putExtra(Repo.TAG, mRepo);
        getRawActivity().startActivity(intent);
    }

    @Override
    public void reset() {
        if (mLoadding == null || mStatus == null)
            return;
        mLoadding.setVisibility(View.VISIBLE);
        mStatus.setVisibility(View.GONE);
        StatusTask task = new StatusTask(mRepo, result -> {
            mStatus.setText(result);
            mLoadding.setVisibility(View.GONE);
            mStatus.setVisibility(View.VISIBLE);
        });
        task.executeTask();
    }

    @Override
    public OnBackClickListener getOnBackClickListener() {
        return null;
    }

    // 保存状态
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mRepo != null) {
            outState.putSerializable(Repo.TAG, mRepo);
        }
    }
}
