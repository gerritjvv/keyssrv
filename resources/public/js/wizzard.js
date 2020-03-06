$(function () {

    // click select support for each pricing card
    $('.card-pricing').click(function (e) {
        $('.card-pricing').each ( function () {

            $(this).addClass('card-plain');
            // $(this).removeClass('bg-danger');
            $(this).removeClass('card-raised');


            $(this).find(".radio").each(function () {
                $(this).prop("checked", false);
            });
        });

        // $(this).addClass('bg-danger');
        $(this).addClass('card-raised');
        $(this).removeClass('card-plain');

        $(this).siblings('.radio').prop("checked", true).trigger("click");

        $(this).find(".radio").each(function () {
           $(this).prop("checked", true);
        });

        $("#next").removeAttr("disabled");
        $("#next").val("Next");
        $("#next").unbind('click');
        $('#next').click(checkClickNext);


        $('#account-selected-lbl').text($(this).data('display-lbl'));
    });

    function submitSetupWizzardForm(e) {

        $("#next").attr("disabled", "disabled");

        e.stopImmediatePropagation();
        //store stripe info and submit form
        paymentUpdateFunction(e);
        $("#next").removeAttr("disabled");

    }

    // function that fires when the next or finish buttons are clicked
    // change how the wizzard behaves based on what the user selected for pricing
    //  Free plans show: Price Plans + Email Confirm tabs
    //  Paid plans show: Price Plans + Billing
    function checkClickNext () {


        //we require confirm key from free plans
        if($("#free-year").is(':checked') || $("#free-month").is(':checked')) {

            // free plan selected
            // show the email tab
            $('a[href="#confirm-email"]').trigger('click');

        } else {
            // go to billing
            $('a[href="#billing"]').trigger('click');
        }

    }

    function checkClickPrevious() {
        //noop
        if($("#free-year").is(':checked') || $("#free-month").is(':checked')) {

            // free plan selected
            // go back to details and not billing
            $('a[href="#details"]').trigger('click');
        }
        
        console.log($("#free-year"));
    }


    $('#next').click(checkClickNext);
    $("#previous").click(checkClickPrevious);


    // -- required that when we are viewing stripe, the next button performs a submit
    var wizzardTabHref = "";


    $('a[data-toggle="tab"]').on('shown.bs.tab', function (e) {

        var href = $(e.target).attr("href");

        if ( wizzardTabHref == "#billing" && href != "#billing") {
            paymentUpdateFunction(e);
        }

        wizzardTabHref = href;

        /* Event when each tab-pane in the wizzard is shown */
        var target = $(e.target).attr("href") // activated tab

        if(target == "#billing"){

            // set the buttons up for the bi;ling pane

            $("#confirm-code").prop('required',false);
            $("#next").val($("#finish").val());
            $("#next").unbind().click(submitSetupWizzardForm);


        } else if (target == "#details") {
            // set the buttons up for the details pane

            $('#next').unbind().click(checkClickNext);
            $("#previous").unbind().click(checkClickPrevious);
            $("#next").val("Next");



        } else if (target == "#confirm-email" ){
            //#confirm-email

            $('#next').unbind().click(checkClickNext);
            $("#previous").unbind().click(checkClickPrevious);

            $("#confirm-code").prop('required',true);
            $("#next").val($("#finish").val());


        }
    });

    // -- end stripe

    // select the plan from the pre-selected values the user chose pre register
    var preSelectedPlanPeriod = $('#pre-selected-plan-period').val();
    var preSelectedPlan = $('#pre-selected-plan').val()

    var selectedPlanId = preSelectedPlan + "-" + preSelectedPlanPeriod;

    $('#' + selectedPlanId).click();

    //if any preselected plan that is not free, we continue to next in the wizzard
    if(!(typeof preSelectedPlan === 'undefined' || preSelectedPlan === null || preSelectedPlan == '')) {
        setTimeout(function () {
            checkClickNext();
        }, 1000);
    }
});