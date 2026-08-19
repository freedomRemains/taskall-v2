//-------------------------------------------------------------------------//
// 汎用的な処理
//-------------------------------------------------------------------------//

// メインフォームの送信ボタンをクリックしたときの処理
function submitMainForm() {

  // メインフォームをサブミットする
  mainForm.submit();
}

// JavaScript関数でHTMLのリンククリックと同じ挙動を実現する
function redirectByUrl(url) {

  // HTMLのリンククリックと同じ挙動を実現する
  window.location.href = url;
}

//-------------------------------------------------------------------------//
// 個別の処理(TODO 多くなったらファイルを分割する)
//-------------------------------------------------------------------------//

// 1ページに表示するレコード件数を変更する処理
function changeLimit(urlBase, offset) {

  // IDによりselectの値を取得する
  var limit = document.getElementById('selectLimit').value;

  // リンククリック(GETリクエスト)により画面遷移する
  redirectByUrl(urlBase + limit + offset);
}

// 案件一覧のページ送り処理(offset隠しフィールドを書き換えてから、属性検索の
// チェック状態を維持したままメインフォームを再送信する)
function changeAnkenOffset(offset) {

  document.getElementById('ankenOffset').value = offset;
  submitMainForm();
}
// 一括削除の確認処理
function confirmBulkDelete() {

  // 操作確認を行い、キャンセルならば何もしない
  const result = confirm('一括削除を実行します。よろしいですか？\n(この操作は取り消せません)');
  if (!result) {
    return;
  }

  // メインフォームをサブミットする
  submitMainForm();
}

// 属性検索のチェックボックス状態をセッションストレージへ保存するキー
const ANKEN_ATTR_CHECK_STORAGE_KEY = 'ankenAttrSearchChecks';

// 属性検索フォームのチェック状態をセッションストレージへ保存する処理
// (絞り込み結果自体は保存せず、見た目のチェック状態のみを記憶する。POST再送信によるF5警告を避けるため、
//  検索実行時にチェック状態を保存しておき、F5後のGET表示時にJavaScriptで見た目だけ復元する)
function saveAnkenAttrChecks() {

  const checkedIds = [];
  document.querySelectorAll('[id^="attr"]:checked').forEach(function (checkbox) {
    checkedIds.push(checkbox.id);
  });
  sessionStorage.setItem(ANKEN_ATTR_CHECK_STORAGE_KEY, JSON.stringify(checkedIds));
}

// ページ読み込み時、セッションストレージに保存された属性検索のチェック状態を復元する処理
function restoreAnkenAttrChecks() {

  const saved = sessionStorage.getItem(ANKEN_ATTR_CHECK_STORAGE_KEY);
  if (!saved) {
    return;
  }

  const checkedIds = JSON.parse(saved);
  checkedIds.forEach(function (id) {
    const checkbox = document.getElementById(id);
    if (checkbox) {
      checkbox.checked = true;
    }
  });
}
