package com.madgag.agit;

import static com.google.common.collect.Lists.transform;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.RenameDetector;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.common.base.Function;
import com.madgag.agit.DiffSliderView.OnStateUpdateListener;
import com.madgag.agit.LineContextDiffer.Hunk;

public class CommitChangeListAdapter extends BaseExpandableListAdapter implements OnStateUpdateListener {


		private LayoutInflater mInflater;
		private final DiffSliderView diffSlider;
		private final ExpandableListView expandableList;
		private final Context context;
		private final RevCommit commit;
		private final Repository repository;
		

		List<FileDiff> fileDiffs;
		Map<Long, DiffText> diffTexts=new HashMap<Long, DiffText>();

		public CommitChangeListAdapter(Repository repository, RevCommit commit, DiffSliderView diffSlider, ExpandableListView expandableList, Context context) {
			this.repository = repository;
			this.commit = commit;
			this.diffSlider = diffSlider;
			this.expandableList = expandableList;
			this.context = context;
			mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			diffSlider.setStateUpdateListener(this);
			try {
				setupFileDiffs(new RevWalk(repository));
			} catch (Exception e) { throw new RuntimeException(e); }
		}

		public Object getChild(int groupPosition, int childPosition) {
			return fileDiffs.get(groupPosition).getHunks().get(childPosition);
		}

		public long getChildId(int arg0, int arg1) {
			return 0;
		}

		public View getChildView(int groupPosition, int childPosition,
				boolean isLastChild, View convertView, ViewGroup parent) {
			Hunk hunk = fileDiffs.get(groupPosition).getHunks().get(childPosition);
			HunkDiffView v;
			// Disabling view re-use for Children - too unpredicateable, can not easily tell when my difftext should be invalidated!
//			if (convertView==null || !(convertView instanceof HunkDiffView)) {
				v=new HunkDiffView(context, hunk);
//			} else {
//				v=((HunkDiffView)convertView);
//				v.setHunk(hunk);
//			}
			diffTexts.put(keyFor(groupPosition, childPosition), v.getDiffText());
			return v;
		}

		public int getChildrenCount(int groupPosition) {
			return fileDiffs.get(groupPosition).getHunks().size();
		}

		public Object getGroup(int index) {
			return fileDiffs.get(index);
		}

		public int getGroupCount() {
			return fileDiffs.size();
		}

		public long getGroupId(int index) {
			return fileDiffs.get(index).hashCode(); // Pretty lame
		}

		public View getGroupView(int groupPosition, boolean isExpanded,
				View convertView, ViewGroup parent) {
			View v;
			if (convertView == null) {
				v = newGroupView(isExpanded, parent);
			} else {
				v = convertView;
			}
			DiffEntry diffEntry = fileDiffs.get(groupPosition).getDiffEntry();
			int changeTypeIcon = R.drawable.diff_changetype_modify;
			String filename = diffEntry.getNewPath();
			switch (diffEntry.getChangeType()) {
			case ADD:
				changeTypeIcon = R.drawable.diff_changetype_add;
				break;
			case DELETE:
				changeTypeIcon = R.drawable.diff_changetype_delete;
				filename = diffEntry.getOldPath();
				break;
			case MODIFY:
				changeTypeIcon = R.drawable.diff_changetype_modify;
				break;
			case RENAME:
				changeTypeIcon = R.drawable.diff_changetype_rename;
				filename = nameChange(diffEntry);
				break;
			case COPY:
				changeTypeIcon = R.drawable.diff_changetype_add;
				break;
			}
			((ImageView) v.findViewById(R.id.commit_file_diff_type))
					.setImageResource(changeTypeIcon);
			((TextView) v.findViewById(R.id.commit_file_textview))
					.setText(filename);

			return v;
		}

		private String nameChange(DiffEntry diffEntry) {
			return new FilePathDiffer().diff(diffEntry.getOldPath(), diffEntry.getNewPath());
		}

		private View newGroupView(boolean isExpanded, ViewGroup parent) {
			return mInflater.inflate(isExpanded ? R.layout.commit_group_view
					: R.layout.commit_group_view, parent, false);
		}

		public boolean hasStableIds() {
			return false;
		}

		public boolean isChildSelectable(int arg0, int arg1) {
			return false;
		}

		public void onStateChanged(DiffSliderView diffSliderView, float state) {
			for (int i=0;i<fileDiffs.size();++i) {
				if (expandableList.isGroupExpanded(i)) {
					for (int j=0;j<getChildrenCount(i);++j) {
						DiffText diffText = diffTexts.get(keyFor(i, j));
						if (diffText!=null) {
							diffText.setTransitionProgress(state);
						}
					}
				}
			}
		}

		private long keyFor(int i, int j) {
			return (((long) i) << 32) + j;
		}


		private List<DiffEntry> detectRenames(List<DiffEntry> files) throws IOException {
			RenameDetector rd = new RenameDetector(repository);
//			if (renameLimit != null)
//				rd.setRenameLimit(renameLimit.intValue());
			rd.addAll(files);
			return rd.compute();
		}
		

		private List<FileDiff> setupFileDiffs(RevWalk revWalk) throws MissingObjectException,
				IncorrectObjectTypeException, IOException, CorruptObjectException {
			final TreeWalk tw = new TreeWalk(revWalk.getObjectReader());
			tw.setRecursive(true);
			tw.reset();
			RevCommit commitParent = revWalk.parseCommit(commit.getParent(0));
			RevTree commitParentTree = revWalk.parseTree(commitParent.getTree());
			tw.addTree(commitParentTree);
			RevTree commitTree = revWalk.parseTree(commit.getTree());
			tw.addTree(commitTree);
			TreeFilter pathFilter = TreeFilter.ANY_DIFF;
			tw.setFilter(pathFilter);
			List<DiffEntry> files = DiffEntry.scan(tw);
			Log.i("RCCV", files.toString());

			boolean detectRenames=true;
//					if (pathFilter instanceof FollowFilter && isAdd(files)) {
				// The file we are following was added here, find where it
				// came from so we can properly show the rename or copy,
				// then continue digging backwards.
				//
				
//						tw.reset();
//						tw.addTree(commitParentTree);
//						tw.addTree(commitTree);
//						tw.setFilter(pathFilter);
//						files = updateFollowFilter(detectRenames(DiffEntry.scan(tw)));
	//
//					} else 
			if (detectRenames)
				files = detectRenames(files);

			final LineContextDiffer lineContextDiffer = new LineContextDiffer(revWalk.getObjectReader());
			return transform(files, new Function<DiffEntry,FileDiff>() {
				public FileDiff apply(DiffEntry d) { return new FileDiff(lineContextDiffer, d); }
			});
		}
	}