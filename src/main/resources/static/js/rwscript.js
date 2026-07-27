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
