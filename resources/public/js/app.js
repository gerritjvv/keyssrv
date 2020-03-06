function loginRegisterReCapchaCallback(args) {
    $("#g-recaptcha-response").val(args);
    $("#registerform").submit();
}

function scorePassword(pass) {
    //https://stackoverflow.com/questions/948172/password-strength-meter
    var score = 0;
    if (!pass)
        return score;

    // award every unique letter until 5 repetitions
    var letters = new Object();
    for (var i = 0; i < pass.length; i++) {
        letters[pass[i]] = (letters[pass[i]] || 0) + 1;
        score += 5.0 / letters[pass[i]];
    }

    // bonus points for mixing it up
    var variations = {
        digits: /\d/.test(pass),
        lower: /[a-z]/.test(pass),
        upper: /[A-Z]/.test(pass),
        nonWords: /\W/.test(pass),
    }

    variationCount = 0;
    for (var check in variations) {
        variationCount += (variations[check] == true) ? 1 : 0;
    }
    score += (variationCount - 1) * 10;

    return parseInt(score);
}

function checkPassStrength(pass) {
    var score = scorePassword(pass);
    if (score > 80)
        return "strong";
    if (score > 60)
        return "good";

    return "weak";

}

function ifExistSingle(k, fn) {

    var v = $(k);

    if (v.length > 0) {
        fn(v);
    }
}

function ifExist(k, fn) {

    var v = $(k);

    if (v.length == 0) {
        fn(v);
    } else if (v.length > 0) {
        for (i = 0; i < v.length; i++) {
            fn(v[i]);
        }
    }
}

function parseAndShowPageHints(enjoyhint_instance, hintSelector) {
    //simple config.
    //Only one step - highlighting(with description) "New" button
    //hide EnjoyHint after a click on the button.
    // console.log(hintSelector.text())

    var enjoyhint_script_steps = JSON.parse(hintSelector.text());

    //set script config
    enjoyhint_instance.set(enjoyhint_script_steps);

    //run Enjoyhint script
    enjoyhint_instance.run();
}

//A button that contains template information to insert into a text area or field
//the e is the element returned froma click action, the element shouold have a data-id
//that points to the text are and a data-value that is the value to insert
function insertDelegateAction(e) {
    trigger = $(e.target);

    element = $("#" + trigger.attr('data-id'));
    elementVal = trigger.attr('data-val');

    element.val(elementVal);
}

//passgroups_envs.html when validating the env yaml we expect
// the vlaidate-status field to be present and will show success or errors
// this uses the js-yaml library merged into app.js
function validateEnvYaml(e) {
    var trigger = $(e.target);

    var element = $("#" + trigger.attr('data-id'));
    var statusElement = $("#" + trigger.attr('data-status'));

    try {
        var doc = jsyaml.safeLoad(element.val());

        statusElement.html("Valid");
        statusElement.removeClass("btn-outline-danger");
        statusElement.addClass("btn-outline-success");

    } catch (e) {
        statusElement.html(e.message);

        statusElement.removeClass("btn-outline-success");
        statusElement.addClass("btn-outline-danger");

    }
}

function styleSelectPicker() {
    //style all selects
    $('.selectpicker').selectpicker();
};


function copyWrite() {
    var year = new Date().getFullYear();
    $("#copyright").html("&copy;" + year + " Newtecnia Solutions Ltd");
}

$(function () {


    copyWrite();

    styleSelectPicker();

    //passgroups_envs.html create new env
    $().ready(function () {
        $("#insert-k8s-template").click(insertDelegateAction);
        $("#insert-bash-template").click(insertDelegateAction);
        $("#update-k8s-template").click(insertDelegateAction);
        $("#update-bash-template").click(insertDelegateAction);

        $("#validate-yaml").click(validateEnvYaml);
        $("#validate-yaml2").click(validateEnvYaml);

    });


    // $(document).ready(function () {
    //     $('#search-data-table').DataTable({
    //         // responsive: true,
    //         // scrollY:        '70%'
    //         // "scrollX": true,
    //         stateSave: true,
    //     });
    //
    //     //starts tooltips on all data-toggle fields
    //     $('[data-toggle="tooltip"]').tooltip();
    // });

    // toggle password visibility
    $('#password + .fa').on('click', function (e) {

        $(this).toggleClass('fa-eye-slash').toggleClass('fa-eye'); // toggle our classes for the eye icon

        if ($('#password')[0].type == "password") {
            $('#password')[0].type = "text";
        } else {
            $('#password')[0].type = "password";
        }

    });


    ifExist("#password-strength-text", function (e) {

        $("#password").on('input', function (p) {

            var passStrength = checkPassStrength($("#password").val());

            var el = $("#password-strength-text");

            el.text(passStrength);
            if (passStrength == "strong")
                el.addClass("strong-text");
            else if (passStrength == "good")
                el.addClass("good-text");
            else
                el.addClass("error-text");

        })
    });

    /**
     * Search for fields user-name and email
     * then launch a ajax check to see if they already exist or not
     * This requires a valid __anti-forgery-token exist on the page
     *
     * Used in login and register pages
     */
    var checkStatusNames = ["user-name", "email"];

    for (i = 0; i < checkStatusNames.length; i++) {
        var elName = checkStatusNames[i];


        ifExistSingle("[data-check-status=" + elName + "]", function (e) {


            var txt = e.attr('data-text-taken');

            e.on('blur', function () {

                var val = e.val();

                csrfToken = document.getElementById("__anti-forgery-token").value;


                if (val.length > 1) {
                    $.ajax({
                        url: '/register/check-exist',
                        type: 'POST',
                        data: {
                            "check-type": e[0].id,
                            "check-value": val,
                            "__anti-forgery-token": csrfToken
                        },
                        success: function (response) {

                            if (response.resp == "0" || response.resp == "-1") {
                                //does not exist
                                e.parent().addClass("form_success");
                                e.siblings("span").text("");
                            } else {
                                //exist
                                e.parent().addClass("form_error");
                                e.siblings("span").text(txt);
                            }
                        }
                    });

                }
            });

        });
    }


    ifExist('#updateloginModal', function (e) {
        $('#updateloginModal').on('show.bs.modal', function (e) {

            //get data-N attribute of the clicked element

            //populate the textbox
            $(e.currentTarget).find('input[name="group-login-id"]').val($(e.relatedTarget).data('id'));
            $(e.currentTarget).find('input[name="new-lbl"]').val($(e.relatedTarget).data('lbl'));

            $(e.currentTarget).find('input[name="new-login"]').val($(e.relatedTarget).data('login'));
            $(e.currentTarget).find('input[name="new-user-name"]').val($(e.relatedTarget).data('user-name'));
            $(e.currentTarget).find('input[name="new-user-name2"]').val($(e.relatedTarget).data('user-name2'));

        });
    });

    ifExist('#updateSecretModal', function (e) {
        $('#updateSecretModal').on('show.bs.modal', function (e) {

            //get data-id attribute of the clicked element
            var lbl = $(e.relatedTarget).data('lbl');

            //populate the textbox
            $(e.currentTarget).find('input[name="current-lbl"]').val(lbl);
        });
    });


    function sendResendConfirmEmail() {
        csrfToken = document.getElementById("__anti-forgery-token").value;

        // see setup_wizzard.html, this action will trigger a resend of the confirm email
        $.ajax({
            url: '/home',
            type: 'POST',
            data: {
                "action": "resend-confirm",
                "__anti-forgery-token": csrfToken
            }, success: function (resp) {
                // console.log(resp);
            }
        });
    }

    ifExist('#resendConfirmEmail', function (e) {

        $('#resendConfirmEmail').click(function () {
            sendResendConfirmEmail();
            $('#emailSent').modal('show');
        });
    });

    //////////////// enjoy hint
    ///////////see https://github.com/xbsoftware/enjoyhint/

    // function turnOffHintsWizzard() {
    //     csrfToken = document.getElementById("__anti-forgery-token").value;
    //
    //     $.ajax({
    //         url: '/pass/groups',
    //         type: 'POST',
    //         data: {
    //             "action": "hints-end",
    //             "__anti-forgery-token": csrfToken
    //         }, success: function (resp) {
    //             console.log(resp);
    //         }
    //     });
    // }


    // var hintSelector = $("#hintSelector");
    // //
    // // console.log("Hint selector: ");
    // // console.log(hintSelector);
    //
    // if (hintSelector.length > 0 && hintSelector.data("show-on-load") == "1") {
    //
    //     var doAjax = true;
    //
    //     if (hintSelector.data("no-ajax") == "1") {
    //         doAjax = false;
    //     }
    //
    //     var enjoyhint_instance = new EnjoyHint({
    //         onEnd: function () {
    //             if (doAjax) {
    //                 turnOffHintsWizzard();
    //             }
    //         },
    //         onSkip: function () {
    //
    //             if (doAjax) {
    //                 turnOffHintsWizzard();
    //             }
    //         }
    //     });
    //     parseAndShowPageHints(enjoyhint_instance, hintSelector);
    // }


    ////////////////// Page help link

    // var pageHelpLink = $("#page-help-link");
    //
    //
    // if (pageHelpLink.length > 0) {
    //     pageHelpLink.on('click', function () {
    //
    //         if (hintSelector.length > 0) {
    //
    //             var enjoyhint_instance = new EnjoyHint({});
    //             parseAndShowPageHints(enjoyhint_instance, hintSelector);
    //         }
    //     })
    // }


    //---- SSE Events
    if (typeof(EventSource) !== "undefined") {
        //Check browser support

        var date = new Date();

        var passGroupSseId = $('#group-id')
        var viewSseId = $('#view-id')

        if (passGroupSseId.length > 0 && viewSseId.length > 0) {
            var source = new EventSource('/sse/pass/groups?gid=' + passGroupSseId.val() + "&v=" + viewSseId.val() + "&ts=" + date.getTime(),
                {withCredentials: true});
            source.addEventListener("refresh", function (e) {
                // console.log(e);
                if (e.data == "1") {
                    var alertDiv = $('#alert-warning')

                    alertDiv.html("<span>The page you are viewing has been updated. <a href=\"\">Reload</a> to refresh.</span>")
                    alertDiv.removeAttr('hidden');

                } else {
                    // console.log("no refresh")
                }
            });

        }

    }


    $("#btnPricingFreePlanYear").click(function () {
        gaClickPricingPlan('free-year');
    });

    $("#btnPricingFreePlanMonth").click(function () {
        gaClickPricingPlan('free-month');
    });

    $("#btnPricingBasicPlanYear").click(function () {
        gaClickPricingPlan('basic-year');
    });

    $("#btnPricingBasicPlanMonth").click(function () {
        gaClickPricingPlan('basic-month');
    });

    $("#btnPricingProPlanYear").click(function () {
        gaClickPricingPlan('pro-year');
    });

    $("#btnPricingProPlanMonth").click(function () {
        gaClickPricingPlan('pro-month');
    });


    $("#btnPricingProPlanMonth").click(function () {
        gaClickPricingPlan('pro-month');
    });

    $("#btnSignupTop").click(function () {
        gaClickSingup('top');
    });

    $("#btnSignupMid").click(function () {
        gaClickSingup('mid');
    });

    $("#btnSignupFinal").click(function () {
        gaClickSingup('final');
    });


    gaSetup();
    // zohoSetup();

    setupClipBoard();

    var ssShowLink = $("#ss-show-link");
    if (ssShowLink.length) {
        $('#showLinkModal').modal('show');

        var linkText = "https://" + window.location.host + "/"
        linkText += ssShowLink.data('link');

        $('#secureLink').text(linkText.replace('&amp;', '&'));
    }

    var ssShowMsg = $("#ss-show-msg");
    if (ssShowMsg.length) {
        $('#showMessageModal').modal('show');
        $('#secureMessage').text(ssShowMsg.data('msg'));
    }

    var ssNeedCode = $("#ss-need-code");
    if (ssNeedCode.length) {
        $('#needCodeModal').modal('show');
    }

    // mfa/show_data sets the img src to the correct qr data, our scp doesn't allow inline setting
    var qrData = $("#qr-img").data("qr-src");
    if(qrData) {
        $("#qr-img").prop("src", qrData);
    }


    fixHttpsToForms();

});


// ensures that all forms have https as their prefix and point explicitly to the location they were downloaded from
function fixHttpsToForms() {

    var forms = $('form');

    if(forms) {
        for (var i = 0; i < forms.length; i++) {
            var action = forms[i].action;

            if(action.startsWith("http://")) {
                console.log("Debug: Forcing form action to https for: " + forms[i].action);
                if(!action.startsWith("http://localhost")) {
                    forms[i].action = action.replace("http://", "https://");
                }
            }
        }
    }
}

function gaSetup() {
    window.dataLayer = window.dataLayer || [];

    function gtag() {
        dataLayer.push(arguments);
    }

    gtag('js', new Date());

    gtag('config', 'UA-131761779-1');
}

function gaClickSingup(lbl) {
    if (gtag) {
        gtag('send', 'event', 'signup', 'click', lbl);
    }
}

function gaClickPricingPlan(lbl) {
    if (gtag) {
        gtag('send', 'event', 'pricing', 'click', lbl);
    }
}

function setTooltip(e, message) {

    e.tooltip('hide')
        .attr('data-original-title', message)
        .tooltip('show');
}

function hideTooltip(e) {
    setTimeout(function () {
        e.tooltip('hide');
    }, 1000);
}

function setupClipBoard() {

    var clipboard = new ClipboardJS('.btn');

    clipboard.on('success', function (e) {
        trigger = $(e.trigger);
        trigger.tooltip('hide');

        element = $(trigger.attr('data-clipboard-target'));

        setTooltip(element, 'Copied!');
        hideTooltip(element);

    });

    clipboard.on('error', function (e) {
        trigger = $(e.trigger);

        trigger.tooltip('hide');

        element = $(trigger.attr('data-clipboard-target'));

        setTooltip(element, 'Failed!');
        hideTooltip(element);
    });
}


function gen_pass(len) {
    array = new Uint8Array(len);

    window.crypto.getRandomValues(array);

    var validChars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-/:,\"\'*$()%';

    for (var i = 0; i < len; i++) {
        array[i] = validChars.charCodeAt(array[i] % validChars.length);
    }

    randomState = String.fromCharCode.apply(null, array);

    return randomState;
}

function set_secret_pass(lbl, len) {
    $(lbl).val(gen_pass(len));
}


function destroyClickedElement(event) {
    document.body.removeChild(event.target);
}

function saveTextAsFile(e, filename) {
    var textToWrite = document.getElementById(e).value;

    var textFileAsBlob = new Blob([textToWrite], {type: 'text/plain'});

    var downloadLink = document.createElement("a");
    downloadLink.download = filename;
    downloadLink.innerHTML = "Download File";
    if (window.webkitURL != null) {
        // Chrome allows the link to be clicked
        // without actually adding it to the DOM.
        downloadLink.href = window.webkitURL.createObjectURL(textFileAsBlob);
    }
    else {
        // Firefox requires the link to be added to the DOM
        // before it can be clicked.
        downloadLink.href = window.URL.createObjectURL(textFileAsBlob);
        downloadLink.onclick = destroyClickedElement;

        downloadLink.style.display = "none";
        document.body.appendChild(downloadLink);
    }

    downloadLink.click();
}

//read a file from a input type file and set the value to the text area
function syncTextFile(fileReader, textArea) {
    fileE = document.getElementById(fileReader);
    textAreaE = document.getElementById(textArea);


    if (fileE.files && fileE.files.length > 0) {

        var reader = new FileReader();
        reader.onload = function (e) {
            // var textArea = document.getElementById(textArea);
            textAreaE.value = e.target.result;
        };
        reader.readAsText(fileE.files[0]);
    }

}
